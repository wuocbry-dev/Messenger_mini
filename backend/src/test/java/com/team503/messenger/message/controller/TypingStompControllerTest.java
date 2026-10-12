package com.team503.messenger.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.TypingIndicator;
import com.team503.messenger.message.dto.TypingRequest;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class TypingStompControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    private ConversationMemberRepository memberRepository;
    private UserRepository userRepository;
    private SimpMessagingTemplate messagingTemplate;
    private TypingStompController controller;

    @BeforeEach
    void setUp() {
        memberRepository = Mockito.mock(ConversationMemberRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        controller = new TypingStompController(memberRepository, userRepository, messagingTemplate, CLOCK);
    }

    @Test
    void typingIsDeliveredOnlyToOtherMembersPersonalQueue() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        Mockito.when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        ConversationMember bob = Mockito.mock(ConversationMember.class);
        Mockito.when(bob.getUserId()).thenReturn(1L);
        ConversationMember alice = Mockito.mock(ConversationMember.class);
        Mockito.when(alice.getUserId()).thenReturn(2L);
        Mockito.when(memberRepository.findActiveByConversationId(1L)).thenReturn(List.of(bob, alice));
        User aliceUser = Mockito.mock(User.class);
        Mockito.when(aliceUser.getUsername()).thenReturn("alice");
        Mockito.when(userRepository.findAllById(Set.of(2L))).thenReturn(List.of(aliceUser));

        controller.typing(new TypingRequest(1L, true), newAccessorWithAuth(auth));

        verify(messagingTemplate).convertAndSendToUser(
                eq("alice"),
                eq("/queue/typing"),
                any(TypingIndicator.class)
        );
        verify(messagingTemplate, never()).convertAndSendToUser(eq("bob"), anyString(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void typingRejectsNonMember() {
        AuthenticatedUser principal = new AuthenticatedUser(99L, "eve");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        Mockito.when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> controller.typing(new TypingRequest(1L, true), newAccessorWithAuth(auth)))
                .isInstanceOf(ConversationException.class)
                .satisfies(e -> {
                    ConversationException ce = (ConversationException) e;
                    assertThat(ce.getCode()).isEqualTo("NOT_A_MEMBER");
                });
    }

    @Test
    void typingRejectsUnauthenticated() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("test-session");
        headers.setLeaveMutable(true);

        assertThatThrownBy(() -> controller.typing(new TypingRequest(1L, true), headers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unauthenticated");
    }

    private SimpMessageHeaderAccessor newAccessorWithAuth(UsernamePasswordAuthenticationToken auth) {
        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId("test-session");
        accessor.setLeaveMutable(true);
        if (auth != null) {
            accessor.setUser(auth);
        }
        return accessor;
    }
}
