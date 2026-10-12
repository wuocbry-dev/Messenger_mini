package com.team503.messenger.presence.service;

import com.team503.messenger.presence.dto.PresenceUpdate;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks online/offline state for connected users in memory, debounces writes
 * of {@code users.last_seen_at} to MySQL, and publishes presence updates to
 * interested STOMP subscribers.
 *
 * <p>Multiple browser sessions for the same user are handled by a
 * {@code ConcurrentHashMap<userId, Set<sessionId>>}. A user is considered
 * online if at least one session is registered; going offline is only
 * triggered when the last session disconnects.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /**
     * Minimum interval between writes to {@code users.last_seen_at}. Presence
     * events within this window do not trigger an UPDATE.
     */
    static final Duration LAST_SEEN_DEBOUNCE = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    private final ConcurrentHashMap<Long, Set<String>> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LocalDateTime> lastPersistedSeen = new ConcurrentHashMap<>();

    public PresenceService(
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public boolean isOnline(Long userId) {
        Set<String> userSessions = sessions.get(userId);
        return userSessions != null && !userSessions.isEmpty();
    }

    /**
     * Register a new STOMP session for the given user. Publishes a presence
     * "online" update if this is the user's first active session.
     */
    @Transactional
    public void sessionConnected(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        boolean[] wasOfflineHolder = new boolean[1];
        Set<String> userSessions = sessions.compute(userId, (k, v) -> {
            if (v == null) {
                wasOfflineHolder[0] = true;
                return ConcurrentHashMap.newKeySet();
            }
            return v;
        });
        userSessions.add(sessionId);
        if (wasOfflineHolder[0]) {
            LocalDateTime now = LocalDateTime.now(clock);
            log.debug("User {} came online (session={})", userId, sessionId);
            publish(userId, PresenceUpdate.online(userId, now));
        }
    }

    /**
     * Unregister a STOMP session. If this was the user's last active session,
     * persist {@code last_seen_at} (if a debounce window has elapsed) and
     * publish a presence "offline" update.
     */
    @Transactional
    public void sessionDisconnected(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        boolean wentOffline = false;
        Set<String> userSessions = sessions.get(userId);
        if (userSessions == null) {
            return;
        }
        userSessions.remove(sessionId);
        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
            wentOffline = true;
        }
        if (wentOffline) {
            LocalDateTime now = LocalDateTime.now(clock);
            log.debug("User {} went offline (session={})", userId, sessionId);
            maybePersistLastSeen(userId, now);
            publish(userId, PresenceUpdate.offline(userId, lastPersistedSeen.getOrDefault(userId, now)));
        }
    }

    /**
     * Update {@code users.last_seen_at} for the given user, respecting the
     * debounce window. Returns the new persisted timestamp, or empty if the
     * debounce suppressed the write.
     */
    @Transactional
    public Optional<LocalDateTime> maybePersistLastSeen(Long userId, LocalDateTime now) {
        LocalDateTime previous = lastPersistedSeen.get(userId);
        if (previous != null && Duration.between(previous, now).compareTo(LAST_SEEN_DEBOUNCE) < 0) {
            return Optional.empty();
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userOpt.get();
        user.updateLastSeen(now);
        // No explicit save(): managed entity will flush with the surrounding
        // transaction. We do update the cache so subsequent calls within this
        // window are suppressed.
        lastPersistedSeen.put(userId, now);
        return Optional.of(now);
    }

    private void publish(Long userId, PresenceUpdate update) {
        // We don't have the principal name in the session table here; the
        // presence topic is user-queue addressed, so the user must subscribe to
        // their own queue to see their own status. The other side of presence
        // (letting user A watch user B) is implemented as a future concern;
        // for this task we just publish to the affected user's queue so they
        // can confirm "I am online" across devices.
        // We still want other devices of the same user to get a single
        // notification, which is what /user/{username}/queue/... does.
        String username = lookupUsername(userId);
        if (username != null) {
            messagingTemplate.convertAndSendToUser(username, "/queue/presence", update);
        }
    }

    private String lookupUsername(Long userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse(null);
    }
}
