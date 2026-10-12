package com.team503.messenger.presence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.presence.dto.PresenceUpdate;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class PresenceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private UserRepository userRepository;
    private ConversationMemberRepository memberRepository;
    private SimpMessagingTemplate messagingTemplate;
    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        presenceService = new PresenceService(userRepository, memberRepository, messagingTemplate, CLOCK);

        // Provide a username lookup for publish().
        User user = activeUser(1L, "bob");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(memberRepository.findActivePeerUserIds(1L)).thenReturn(List.of());
    }

    @Test
    void firstSessionConnectPublishesOnline() {
        presenceService.sessionConnected(1L, "session-1");

        assertThat(presenceService.isOnline(1L)).isTrue();

        ArgumentCaptor<PresenceUpdate> captor = ArgumentCaptor.forClass(PresenceUpdate.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("bob"),
                org.mockito.ArgumentMatchers.eq("/queue/presence"),
                captor.capture()
        );
        assertThat(captor.getValue().online()).isTrue();
        assertThat(captor.getValue().userId()).isEqualTo(1L);
    }

    @Test
    void firstSessionConnectPublishesOnlineToActivePeers() {
        User peer = activeUser(2L, "alice");
        when(userRepository.findById(2L)).thenReturn(Optional.of(peer));
        when(memberRepository.findActivePeerUserIds(1L)).thenReturn(List.of(2L));

        presenceService.sessionConnected(1L, "session-1");

        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.eq("/queue/presence"),
                org.mockito.ArgumentMatchers.argThat(update ->
                        update instanceof PresenceUpdate presence
                                && presence.userId().equals(1L)
                                && presence.online())
        );
    }

    @Test
    void sessionConnectSendsPeerPresenceSnapshotToConnectingUser() {
        User peer = activeUser(2L, "alice");
        when(userRepository.findById(2L)).thenReturn(Optional.of(peer));
        when(memberRepository.findActivePeerUserIds(1L)).thenReturn(List.of(2L));
        when(memberRepository.findActivePeerUserIds(2L)).thenReturn(List.of(1L));

        presenceService.sessionConnected(2L, "peer-session");
        org.mockito.Mockito.reset(messagingTemplate);

        presenceService.sessionConnected(1L, "session-1");

        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("bob"),
                org.mockito.ArgumentMatchers.eq("/queue/presence"),
                org.mockito.ArgumentMatchers.argThat(update ->
                        update instanceof PresenceUpdate presence
                                && presence.userId().equals(2L)
                                && presence.online())
        );
    }

    @Test
    void secondSessionConnectDoesNotRepublishOnline() {
        presenceService.sessionConnected(1L, "session-1");
        presenceService.sessionConnected(1L, "session-2");

        assertThat(presenceService.isOnline(1L)).isTrue();
        // Only one online notification — the second session should be silent.
        verify(messagingTemplate, org.mockito.Mockito.times(1))
                .convertAndSendToUser(any(), any(), any(PresenceUpdate.class));
    }

    @Test
    void lastSessionDisconnectPublishesOffline() {
        presenceService.sessionConnected(1L, "session-1");
        presenceService.sessionConnected(1L, "session-2");

        // Two online events have been published: one per first-time-online
        // transition (only the first session triggers it because both
        // sessions are added in the same user).
        org.mockito.Mockito.reset(messagingTemplate);

        presenceService.sessionDisconnected(1L, "session-1");
        assertThat(presenceService.isOnline(1L)).isTrue();
        verify(messagingTemplate, never()).convertAndSendToUser(
                any(), any(), any(PresenceUpdate.class)
        );

        presenceService.sessionDisconnected(1L, "session-2");
        assertThat(presenceService.isOnline(1L)).isFalse();

        ArgumentCaptor<PresenceUpdate> captor = ArgumentCaptor.forClass(PresenceUpdate.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("bob"),
                org.mockito.ArgumentMatchers.eq("/queue/presence"),
                captor.capture()
        );
        PresenceUpdate update = captor.getValue();
        assertThat(update.online()).isFalse();
    }

    @Test
    void lastSeenWriteIsDebouncedWithin60Seconds() {
        User user = activeUser(1L, "bob");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        LocalDateTime t0 = NOW;
        LocalDateTime t1 = NOW.plusSeconds(30);
        LocalDateTime t2 = NOW.plusSeconds(120);

        var firstWrite = presenceService.maybePersistLastSeen(1L, t0);
        var secondWrite = presenceService.maybePersistLastSeen(1L, t1);
        var thirdWrite = presenceService.maybePersistLastSeen(1L, t2);

        assertThat(firstWrite).isPresent();
        assertThat(secondWrite).isEmpty();
        assertThat(thirdWrite).isPresent();
    }

    private User activeUser(Long id, String username) {
        User user = User.register(username, username + "@example.com", "hash", username, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
