package com.team503.messenger.presence.dto;

import com.team503.messenger.user.entity.User;
import java.time.LocalDateTime;

/**
 * Realtime presence snapshot published to {@code /user/{username}/queue/presence}.
 *
 * @param userId     the user whose presence changed
 * @param online     true if the user has at least one live WebSocket session
 * @param lastSeenAt server-known last activity timestamp; null if the user
 *                   has never been seen
 */
public record PresenceUpdate(
        Long userId,
        boolean online,
        LocalDateTime lastSeenAt
) {

    public static PresenceUpdate online(Long userId, LocalDateTime now) {
        return new PresenceUpdate(userId, true, now);
    }

    public static PresenceUpdate offline(Long userId, LocalDateTime lastSeenAt) {
        return new PresenceUpdate(userId, false, lastSeenAt);
    }

    public static PresenceUpdate fromUser(User user, boolean online) {
        return new PresenceUpdate(user.getId(), online, user.getLastSeenAt());
    }
}
