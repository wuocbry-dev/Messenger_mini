package com.team503.messenger.message.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.dto.SendChatMessageRequest;
import com.team503.messenger.message.exception.MessageException;
import com.team503.messenger.message.service.MessageRealtimePublisher;
import com.team503.messenger.message.service.MessageService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ChatStompControllerTest {

    private MessageService messageService;
    private MessageRealtimePublisher realtimePublisher;
    private ChatStompController controller;

    @BeforeEach
    void setUp() {
        messageService = org.mockito.Mockito.mock(MessageService.class);
        realtimePublisher = org.mockito.Mockito.mock(MessageRealtimePublisher.class);
        controller = new ChatStompController(messageService, realtimePublisher);
    }

    @Test
    void sendDerivesSenderFromPrincipalAndPublishesToAllMembers() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        MessageResponse response = sampleResponse(10L, 1L, "bob", "Hello");
        when(messageService.sendChatMessage(eq(1L), eq(1L), any(), anyString())).thenReturn(response);

        SimpMessageHeaderAccessor headers = newAccessorWithAuth(auth);

        controller.send(new SendChatMessageRequest(1L, "TEXT", "Hello"), headers);

        verify(messageService).sendChatMessage(eq(1L), eq(1L), any(), eq("Hello"));
        verify(realtimePublisher).publishToConversation(1L, response);
    }

    @Test
    void sendRejectsUnauthenticatedFrame() {
        SimpMessageHeaderAccessor headers = newAccessorWithAuth(null);

        assertThatThrownBy(() -> controller.send(new SendChatMessageRequest(1L, "TEXT", "Hello"), headers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unauthenticated");

        verify(messageService, never()).sendChatMessage(any(), any(), any(), any());
        verify(realtimePublisher, never()).publishToConversation(any(), any());
    }

    @Test
    void sendDoesNotPublishWhenServiceRejects() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        doThrow(new MessageException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "Not a member"))
                .when(messageService).sendChatMessage(any(), any(), any(), any());

        SimpMessageHeaderAccessor headers = newAccessorWithAuth(auth);

        assertThatThrownBy(() -> controller.send(new SendChatMessageRequest(1L, "TEXT", "Hello"), headers))
                .isInstanceOf(MessageException.class);

        verify(realtimePublisher, never()).publishToConversation(any(), any());
    }

    @Test
    void sendIgnoresClientSuppliedSenderId() {
        // Even if the client tried to fake a senderId, the controller must use
        // the authenticated principal (user 1L) — never the payload.
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        MessageResponse response = sampleResponse(1L, 1L, "bob", "Hello");
        when(messageService.sendChatMessage(any(), any(), any(), anyString())).thenReturn(response);

        SimpMessageHeaderAccessor headers = newAccessorWithAuth(auth);

        controller.send(new SendChatMessageRequest(1L, "text", "Hello"), headers);

        verify(messageService).sendChatMessage(eq(1L), eq(1L), any(), eq("Hello"));
    }

    @Test
    void sendNonTextTypeIsRejected() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        when(messageService.sendChatMessage(any(), any(), any(), anyString()))
                .thenThrow(new MessageException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MESSAGE_TYPE", "Only TEXT"));

        SimpMessageHeaderAccessor headers = newAccessorWithAuth(auth);

        assertThatThrownBy(() -> controller.send(new SendChatMessageRequest(1L, "IMAGE", "img-data"), headers))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("UNSUPPORTED_MESSAGE_TYPE");
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

    private MessageResponse sampleResponse(Long id, Long senderId, String username, String content) {
        return new MessageResponse(
                id, 1L, senderId, username, username, "TEXT", content, LocalDateTime.now(), List.of(), List.of()
        );
    }
}
