package com.team503.messenger.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MessageReceiptUpdate;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ReceiptServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private MessageRepository messageRepository;
    private MessageReceiptRepository receiptRepository;
    private ConversationMemberRepository memberRepository;
    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        messageRepository = org.mockito.Mockito.mock(MessageRepository.class);
        receiptRepository = org.mockito.Mockito.mock(MessageReceiptRepository.class);
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        receiptService = new ReceiptService(
                messageRepository, receiptRepository, memberRepository, CLOCK
        );
    }

    @Test
    void markDeliveredSetsDeliveredAtWhenNoPriorReceipt() {
        // recipient=2L, sender=1L → markDelivered(2L, ...) is allowed
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L, 1L)));
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        when(receiptRepository.fetchByMessageAndUser(10L, 2L)).thenReturn(null);
        when(receiptRepository.save(any(MessageReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageReceipt receipt = receiptService.markDelivered(2L, 10L);

        assertThat(receipt.getDeliveredAt()).isEqualTo(NOW);
        assertThat(receipt.getReadAt()).isNull();
    }

    @Test
    void markDeliveredIsIdempotent() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L, 1L)));
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        MessageReceipt existing = MessageReceipt.forMessage(10L, 2L, NOW.minusMinutes(1));
        when(receiptRepository.fetchByMessageAndUser(10L, 2L)).thenReturn(existing);

        MessageReceipt receipt = receiptService.markDelivered(2L, 10L);

        assertThat(receipt.getDeliveredAt()).isEqualTo(NOW.minusMinutes(1));
    }

    @Test
    void markReadAlsoMarksDelivered() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L, 1L)));
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        when(receiptRepository.fetchByMessageAndUser(10L, 2L)).thenReturn(null);
        when(receiptRepository.save(any(MessageReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageReceipt receipt = receiptService.markRead(2L, 10L);

        assertThat(receipt.getDeliveredAt()).isEqualTo(NOW);
        assertThat(receipt.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void markReadOnExistingDeliveredReceiptKeepsDeliveredAt() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L, 1L)));
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        MessageReceipt existing = MessageReceipt.forMessage(10L, 2L, NOW.minusMinutes(5));
        when(receiptRepository.fetchByMessageAndUser(10L, 2L)).thenReturn(existing);

        MessageReceipt receipt = receiptService.markRead(2L, 10L);

        assertThat(receipt.getDeliveredAt()).isEqualTo(NOW.minusMinutes(5));
        assertThat(receipt.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void senderCannotMarkOwnMessage() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L, 1L)));
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> receiptService.markDelivered(1L, 10L))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("CANNOT_RECEIPT_OWN_MESSAGE");
    }

    @Test
    void nonMemberCannotMark() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L, 1L)));
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> receiptService.markDelivered(99L, 10L))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void missingMessageRejected() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.markDelivered(2L, 999L))
                .isInstanceOf(MessageException.class)
                .extracting(e -> ((MessageException) e).getCode())
                .isEqualTo("MESSAGE_NOT_FOUND");
    }

    private Message message(Long id, Long conversationId, Long senderId) {
        Message m = Message.text(conversationId, senderId, "hello", NOW);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static <T> T any(Class<T> c) {
        return org.mockito.ArgumentMatchers.any(c);
    }
}
