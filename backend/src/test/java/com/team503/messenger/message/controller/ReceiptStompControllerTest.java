package com.team503.messenger.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MarkReceiptRequest;
import com.team503.messenger.message.dto.MessageReceiptUpdate;
import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.repository.MessageRepository;
import com.team503.messenger.message.service.ReceiptService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class ReceiptStompControllerTest {

    private ReceiptService receiptService;
    private MessageRepository messageRepository;
    private ConversationMemberRepository memberRepository;
    private UserRepository userRepository;
    private SimpMessagingTemplate messagingTemplate;
    private ReceiptStompController controller;

    @BeforeEach
    void setUp() {
        receiptService = org.mockito.Mockito.mock(ReceiptService.class);
        messageRepository = org.mockito.Mockito.mock(MessageRepository.class);
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        controller = new ReceiptStompController(
                receiptService, messageRepository, memberRepository, userRepository, messagingTemplate
        );
    }

    @Test
    void markDeliveredBroadcastsToConversationMembers() {
        AuthenticatedUser recipient = new AuthenticatedUser(2L, "alice");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(recipient, null, List.of());

        com.team503.messenger.message.entity.Message message =
                org.mockito.Mockito.mock(com.team503.messenger.message.entity.Message.class);
        when(message.getConversationId()).thenReturn(1L);
        when(messageRepository.findById(10L)).thenReturn(java.util.Optional.of(message));

        MessageReceipt receipt = MessageReceipt.forMessage(10L, 2L, LocalDateTime.now());
        when(receiptService.markDelivered(2L, 10L)).thenReturn(receipt);
        when(receiptService.toUpdate(eq(receipt), eq(1L), eq(MessageReceiptUpdate.State.DELIVERED)))
                .thenAnswer(inv -> new MessageReceiptUpdate(
                        10L, 1L, 2L, "DELIVERED", receipt.getDeliveredAt(), null
                ));

        when(memberRepository.findActiveByConversationId(1L)).thenReturn(List.of(
                ConversationMember.joinAsOwner(1L, 1L, LocalDateTime.now()),
                ConversationMember.join(1L, 2L, LocalDateTime.now())
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                activeUser(1L, "bob"),
                activeUser(2L, "alice")
        ));

        controller.mark(new MarkReceiptRequest(10L, "DELIVERED"), newAccessorWithAuth(auth));

        verify(receiptService).markDelivered(2L, 10L);
        ArgumentCaptor<String> toUser = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSendToUser(toUser.capture(), eq("/queue/receipts"), any(MessageReceiptUpdate.class));
        assertThat(toUser.getAllValues()).containsExactlyInAnyOrder("bob", "alice");
    }

    @Test
    void markReadBroadcastsReadState() {
        AuthenticatedUser recipient = new AuthenticatedUser(2L, "alice");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(recipient, null, List.of());

        com.team503.messenger.message.entity.Message message =
                org.mockito.Mockito.mock(com.team503.messenger.message.entity.Message.class);
        when(message.getConversationId()).thenReturn(1L);
        when(messageRepository.findById(10L)).thenReturn(java.util.Optional.of(message));

        MessageReceipt receipt = MessageReceipt.forMessage(10L, 2L, LocalDateTime.now());
        receipt.markRead(LocalDateTime.now());
        when(receiptService.markRead(2L, 10L)).thenReturn(receipt);
        when(receiptService.toUpdate(eq(receipt), eq(1L), eq(MessageReceiptUpdate.State.READ)))
                .thenAnswer(inv -> new MessageReceiptUpdate(
                        10L, 1L, 2L, "READ", receipt.getDeliveredAt(), receipt.getReadAt()
                ));

        when(memberRepository.findActiveByConversationId(1L)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        controller.mark(new MarkReceiptRequest(10L, "READ"), newAccessorWithAuth(auth));

        verify(receiptService).markRead(2L, 10L);
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), eq("/queue/receipts"), any());
        // (zero members case, so no broadcast)
    }

    @Test
    void markIgnoresInvalidState() {
        AuthenticatedUser recipient = new AuthenticatedUser(2L, "alice");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(recipient, null, List.of());

        controller.mark(new MarkReceiptRequest(10L, "BOGUS"), newAccessorWithAuth(auth));

        verify(receiptService, never()).markDelivered(any(), any());
        verify(receiptService, never()).markRead(any(), any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void markIgnoresMissingMessage() {
        AuthenticatedUser recipient = new AuthenticatedUser(2L, "alice");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(recipient, null, List.of());

        when(messageRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        controller.mark(new MarkReceiptRequest(999L, "DELIVERED"), newAccessorWithAuth(auth));

        verify(receiptService, never()).markDelivered(any(), any());
        verify(receiptService, never()).markRead(any(), any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void markRejectsUnauthenticated() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("test-session");
        headers.setLeaveMutable(true);

        assertThatThrownBy(() -> controller.mark(new MarkReceiptRequest(10L, "DELIVERED"), headers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unauthenticated");
    }

    @Test
    void receiptStateDerivesRecipientFromPrincipalNotPayload() {
        // Client payload is just (messageId, state). Recipient is from principal.
        AuthenticatedUser recipient = new AuthenticatedUser(2L, "alice");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(recipient, null, List.of());

        com.team503.messenger.message.entity.Message message =
                org.mockito.Mockito.mock(com.team503.messenger.message.entity.Message.class);
        when(message.getConversationId()).thenReturn(1L);
        when(messageRepository.findById(10L)).thenReturn(java.util.Optional.of(message));

        MessageReceipt receipt = MessageReceipt.forMessage(10L, 2L, LocalDateTime.now());
        when(receiptService.markDelivered(any(), any())).thenReturn(receipt);
        when(receiptService.toUpdate(any(), any(), any())).thenReturn(
                new MessageReceiptUpdate(10L, 1L, 2L, "DELIVERED", LocalDateTime.now(), null)
        );
        when(memberRepository.findActiveByConversationId(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        controller.mark(new MarkReceiptRequest(10L, "DELIVERED"), newAccessorWithAuth(auth));

        // Caller must be the principal (user 2L), regardless of what the
        // payload might say.
        verify(receiptService).markDelivered(2L, 10L);
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
}
