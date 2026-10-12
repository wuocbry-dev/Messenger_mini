package com.team503.messenger.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.TypingIndicator;
import com.team503.messenger.message.dto.TypingRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class TypingStompControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    private ConversationMemberRepository memberRepository;
    private TypingStompController controller;

    @BeforeEach
    void setUp() {
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        controller = new TypingStompController(memberRepository, CLOCK);
    }

    @Test
    void typingDerivesSenderFromPrincipal() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        org.mockito.Mockito.when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);

        TypingIndicator indicator = controller.typing(new TypingRequest(1L, true), newAccessorWithAuth(auth));

        assertThat(indicator.senderId()).isEqualTo(1L);
        assertThat(indicator.senderUsername()).isEqualTo("bob");
        assertThat(indicator.typing()).isTrue();
        assertThat(indicator.emittedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void typingRejectsNonMember() {
        AuthenticatedUser principal = new AuthenticatedUser(99L, "eve");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        org.mockito.Mockito.when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> controller.typing(new TypingRequest(1L, true), newAccessorWithAuth(auth)))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("NOT_A_MEMBER");
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

    @Test
    void typingAllowsStoppingState() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        org.mockito.Mockito.when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);

        TypingIndicator indicator = controller.typing(new TypingRequest(1L, false), newAccessorWithAuth(auth));

        assertThat(indicator.typing()).isFalse();
    }

    private SimpMessageHeaderAccessor newAccessorWithAuth(UsernamePasswordAuthenticationToken auth) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId("test-session");
        accessor.setLeaveMutable(true);
        if (auth != null) {
            accessor.setUser(auth);
        }
        return accessor;
    }
}
