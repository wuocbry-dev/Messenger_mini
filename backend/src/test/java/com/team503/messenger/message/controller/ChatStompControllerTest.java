package com.team503.messenger.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.dto.SendChatMessageRequest;
import com.team503.messenger.message.exception.MessageException;
import com.team503.messenger.message.service.MessageService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class ChatStompControllerTest {

    private MessageService messageService;
    private ConversationMemberRepository memberRepository;
    private UserRepository userRepository;
    private SimpMessagingTemplate messagingTemplate;
    private ChatStompController controller;

    @BeforeEach
    void setUp() {
        messageService = org.mockito.Mockito.mock(MessageService.class);
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        controller = new ChatStompController(messageService, memberRepository, userRepository, messagingTemplate);
    }

    @Test
    void sendDerivesSenderFromPrincipalAndPublishesToAllMembers() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        MessageResponse response = sampleResponse(10L, 1L, "bob", "Hello");
        when(messageService.sendChatMessage(eq(1L), eq(1L), any(), anyString())).thenReturn(response);

        ConversationMember m1 = ConversationMember.joinAsOwner(1L, 1L, LocalDateTime.now());
        ConversationMember m2 = ConversationMember.join(1L, 2L, LocalDateTime.now());
        when(memberRepository.findActiveByConversationId(1L)).thenReturn(List.of(m1, m2));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                activeUser(1L, "bob"),
                activeUser(2L, "alice")
        ));

        SimpMessageHeaderAccessor headers = newAccessorWithAuth(auth);

        controller.send(new SendChatMessageRequest(1L, "TEXT", "Hello"), headers);

        verify(messageService).sendChatMessage(eq(1L), eq(1L), any(), eq("Hello"));
        ArgumentCaptor<String> toUser = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSendToUser(toUser.capture(), eq("/queue/messages"), eq(response));
        assertThat(toUser.getAllValues()).containsExactlyInAnyOrder("bob", "alice");
    }

    @Test
    void sendRejectsUnauthenticatedFrame() {
        SimpMessageHeaderAccessor headers = newAccessorWithAuth(null);

        assertThatThrownBy(() -> controller.send(new SendChatMessageRequest(1L, "TEXT", "Hello"), headers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unauthenticated");

        verify(messageService, never()).sendChatMessage(any(), any(), any(), any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
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

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void sendIgnoresClientSuppliedSenderId() {
        // Even if the client tried to fake a senderId, the controller must use
        // the authenticated principal (user 1L) — never the payload.
        AuthenticatedUser principal = new AuthenticatedUser(1L, "bob");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        MessageResponse response = sampleResponse(1L, 1L, "bob", "Hello");
        when(messageService.sendChatMessage(any(), any(), any(), anyString())).thenReturn(response);
        when(memberRepository.findActiveByConversationId(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

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

    private User activeUser(Long id, String username) {
        User user = User.register(username, username + "@example.com", "hash", username, LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private MessageResponse sampleResponse(Long id, Long senderId, String username, String content) {
        return new MessageResponse(
                id, 1L, senderId, username, username, "TEXT", content, LocalDateTime.now(), List.of(), List.of()
        );
    }
}
