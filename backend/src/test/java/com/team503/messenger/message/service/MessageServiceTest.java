package com.team503.messenger.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.conversation.entity.Conversation;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.message.dto.MessagePageResponse;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.dto.SendMessageRequest;
import com.team503.messenger.message.entity.Message;
import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.exception.MessageException;
import com.team503.messenger.message.repository.MessageReceiptRepository;
import com.team503.messenger.message.repository.MessageRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

class MessageServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private MessageRepository messageRepository;
    private MessageReceiptRepository receiptRepository;
    private com.team503.messenger.message.repository.MessageAttachmentRepository attachmentRepository;
    private ConversationRepository conversationRepository;
    private ConversationMemberRepository memberRepository;
    private UserRepository userRepository;
    private com.team503.messenger.file.repository.StoredFileRepository storedFileRepository;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageRepository = org.mockito.Mockito.mock(MessageRepository.class);
        receiptRepository = org.mockito.Mockito.mock(MessageReceiptRepository.class);
        attachmentRepository = org.mockito.Mockito.mock(com.team503.messenger.message.repository.MessageAttachmentRepository.class);
        conversationRepository = org.mockito.Mockito.mock(ConversationRepository.class);
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        storedFileRepository = org.mockito.Mockito.mock(com.team503.messenger.file.repository.StoredFileRepository.class);
        messageService = new MessageService(
                messageRepository,
                receiptRepository,
                attachmentRepository,
                conversationRepository,
                memberRepository,
                userRepository,
                storedFileRepository,
                CLOCK
        );
    }

    // --- sendMessage ---

    @Test
    void sendMessagePersistsMessageAndReceiptsForMember() {
        Conversation conv = createConv(1L);
        User sender = activeUser(1L, "bob", "bob@example.com", "Bob");
        User alice = activeUser(2L, "alice", "alice@example.com", "Alice");
        ConversationMember m1 = ConversationMember.joinAsOwner(1L, 1L, NOW);
        ConversationMember m2 = ConversationMember.join(1L, 2L, NOW);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 10L);
            return m;
        });
        when(conversationRepository.save(any())).thenReturn(conv);
        when(memberRepository.findActiveByConversationId(1L)).thenReturn(List.of(m1, m2));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(receiptRepository.findByMessageId(10L)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(sender, alice));

        MessageResponse response = messageService.sendMessage(1L, 1L, new SendMessageRequest("Hello Alice!"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.content()).isEqualTo("Hello Alice!");
        assertThat(response.type()).isEqualTo("TEXT");
        assertThat(response.senderUsername()).isEqualTo("bob");

        verify(messageRepository).save(any(Message.class));
        verify(receiptRepository).save(any(MessageReceipt.class));
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void sendMessageRejectsConversationNotFound() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(1L, 999L, new SendMessageRequest("Hello")))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("CONVERSATION_NOT_FOUND");
    }

    @Test
    void sendMessageRejectsNonMember() {
        Conversation conv = createConv(1L);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.sendMessage(99L, 1L, new SendMessageRequest("Hello")))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void sendMessageRejectsEmptyContent() {
        Conversation conv = createConv(1L);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> messageService.sendMessage(1L, 1L, new SendMessageRequest("")))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("EMPTY_MESSAGE");

        assertThatThrownBy(() -> messageService.sendMessage(1L, 1L, new SendMessageRequest("   ")))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("EMPTY_MESSAGE");

        verify(messageRepository, never()).save(any(Message.class));
    }

    // --- listMessages ---

    @Test
    void listMessagesReturnsPaginatedResultsForMember() {
        User bob = activeUser(1L, "bob", "bob@example.com", "Bob");

        Message m1 = createMessage(1L, 10L, 1L, "Hello", NOW);
        Message m2 = createMessage(1L, 11L, 2L, "Hi bob", NOW.minusMinutes(5));

        when(conversationRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(m1, m2)));
        when(userRepository.findAllById(any())).thenReturn(List.of(bob, activeUser(2L, "alice", "alice@example.com", "Alice")));

        MessagePageResponse response = messageService.listMessages(1L, 1L, 0, 20);

        assertThat(response.messages()).hasSize(2);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void listMessagesRejectsNonMember() {
        when(conversationRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.listMessages(99L, 1L, 0, 20))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void listMessagesRejectsConversationNotFound() {
        when(conversationRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.listMessages(1L, 999L, 0, 20))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("CONVERSATION_NOT_FOUND");
    }

    // --- getMessage ---

    @Test
    void getMessageReturnsMessageForMember() {
        User sender = activeUser(1L, "bob", "bob@example.com", "Bob");
        Message message = createMessage(1L, 10L, 1L, "Hello", NOW);

        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(receiptRepository.findByMessageId(10L)).thenReturn(List.of());

        MessageResponse response = messageService.getMessage(2L, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.content()).isEqualTo("Hello");
        assertThat(response.senderUsername()).isEqualTo("bob");
    }

    @Test
    void getMessageRejectsNonMember() {
        Message message = createMessage(1L, 10L, 1L, "Hello", NOW);

        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.getMessage(99L, 10L))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void getMessageRejectsNotFound() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessage(1L, 999L))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("MESSAGE_NOT_FOUND");
    }

    // --- helpers ---

    private User activeUser(Long id, String username, String email, String displayName) {
        User user = User.register(username, email, "hash", displayName, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Conversation createConv(Long id) {
        Conversation conv = Conversation.createDirect(1L, 2L, 1L, "key", NOW);
        ReflectionTestUtils.setField(conv, "id", id);
        return conv;
    }

    private Message createMessage(Long convId, Long id, Long senderId, String content, LocalDateTime createdAt) {
        Message m = Message.text(convId, senderId, content, createdAt);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}
