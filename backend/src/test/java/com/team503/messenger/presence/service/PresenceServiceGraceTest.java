package com.team503.messenger.presence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.presence.dto.PresenceUpdate;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class PresenceServiceGraceTest {

    private UserRepository users;
    private ConversationMemberRepository members;
    private SimpMessagingTemplate template;
    private PresenceService service;

    @BeforeEach
    void setUp() {
        users = Mockito.mock(UserRepository.class);
        members = Mockito.mock(ConversationMemberRepository.class);
        template = Mockito.mock(SimpMessagingTemplate.class);

        User alice = Mockito.mock(User.class);
        when(alice.getUsername()).thenReturn("alice");
        User bob = Mockito.mock(User.class);
        when(bob.getUsername()).thenReturn("bob");
        when(users.findById(1L)).thenReturn(Optional.of(alice));
        when(users.findById(2L)).thenReturn(Optional.of(bob));
        when(members.findActivePeerUserIds(1L)).thenReturn(List.of(2L));

        // 1s grace keeps the tests quick.
        service = new PresenceService(users, members, template, Clock.systemUTC(), 1);
    }

    private boolean offlinePublishedToBob() {
        try {
            verify(template).convertAndSendToUser(
                    eq("bob"),
                    eq("/queue/presence"),
                    Mockito.argThat((Object o) ->
                            o instanceof PresenceUpdate p && p.userId().equals(1L) && !p.online()));
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    @Test
    void reconnectWithinGraceNeverReportsOffline() throws Exception {
        service.sessionConnected(1L, "s1");
        service.sessionDisconnected(1L, "s1");

        assertThat(offlinePublishedToBob()).isFalse(); // nothing during the grace period

        service.sessionConnected(1L, "s2"); // page reload
        Thread.sleep(1500);

        assertThat(service.isOnline(1L)).isTrue();
        assertThat(offlinePublishedToBob()).isFalse();
    }

    @Test
    void publishesOfflineAndPersistsLastSeenOnceGraceElapses() throws Exception {
        service.sessionConnected(1L, "s1");
        service.sessionDisconnected(1L, "s1");

        Thread.sleep(1600);

        assertThat(service.isOnline(1L)).isFalse();
        assertThat(offlinePublishedToBob()).isTrue();
        verify(users, Mockito.atLeastOnce()).save(any(User.class));
    }

    @Test
    void zeroGraceGoesOfflineImmediately() {
        PresenceService immediate = new PresenceService(users, members, template, Clock.systemUTC());
        immediate.sessionConnected(1L, "s1");
        immediate.sessionDisconnected(1L, "s1");

        assertThat(immediate.isOnline(1L)).isFalse();
        assertThat(offlinePublishedToBob()).isTrue();
        verify(users, never()).save(any(User.class));
    }

    @Test
    void secondSessionKeepsUserOnline() {
        service.sessionConnected(1L, "s1");
        service.sessionConnected(1L, "s2");
        service.sessionDisconnected(1L, "s1");

        assertThat(service.isOnline(1L)).isTrue();
    }
}
