package com.team503.messenger.message.service;

import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MessageReceiptUpdate;
import com.team503.messenger.message.entity.Message;
import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.exception.MessageException;
import com.team503.messenger.message.repository.MessageReceiptRepository;
import com.team503.messenger.message.repository.MessageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks messages as delivered or read. The recipient is taken from the
 * authenticated principal — clients cannot impersonate other users. Idempotent
 * by design: calling markDelivered twice keeps the first timestamp; markRead
 * on a delivered receipt keeps both timestamps.
 */
@Service
public class ReceiptService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final ConversationMemberRepository memberRepository;
    private final Clock clock;

    public ReceiptService(
            MessageRepository messageRepository,
            MessageReceiptRepository receiptRepository,
            ConversationMemberRepository memberRepository,
            Clock clock
    ) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    public MessageReceipt markDelivered(Long recipientId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException(
                        HttpStatus.NOT_FOUND,
                        "MESSAGE_NOT_FOUND",
                        "Message not found"
                ));

        if (!memberRepository.existsActiveMember(message.getConversationId(), recipientId)) {
            throw new MessageException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }

        if (message.getSenderId().equals(recipientId)) {
            // Sender cannot mark their own message as delivered.
            throw new MessageException(
                    HttpStatus.FORBIDDEN,
                    "CANNOT_RECEIPT_OWN_MESSAGE",
                    "Sender cannot mark their own message as delivered"
            );
        }

        MessageReceipt receipt = receiptRepository.findByMessageIdAndUserId(messageId, recipientId);
        LocalDateTime now = LocalDateTime.now(clock);

        if (receipt == null) {
            // The sender-side persistence path normally pre-creates receipts,
            // but support a defensive create in case the message predates the
            // receipt infrastructure.
            receipt = MessageReceipt.forMessage(messageId, recipientId, now);
            return receiptRepository.save(receipt);
        }

        receipt.markDelivered(now);
        return receipt;
    }

    @Transactional
    public MessageReceipt markRead(Long recipientId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException(
                        HttpStatus.NOT_FOUND,
                        "MESSAGE_NOT_FOUND",
                        "Message not found"
                ));

        if (!memberRepository.existsActiveMember(message.getConversationId(), recipientId)) {
            throw new MessageException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }

        if (message.getSenderId().equals(recipientId)) {
            throw new MessageException(
                    HttpStatus.FORBIDDEN,
                    "CANNOT_RECEIPT_OWN_MESSAGE",
                    "Sender cannot mark their own message as read"
            );
        }

        MessageReceipt receipt = receiptRepository.findByMessageIdAndUserId(messageId, recipientId);
        LocalDateTime now = LocalDateTime.now(clock);

        if (receipt == null) {
            // Marking read also implies delivered. If the row does not exist,
            // create it with both timestamps.
            receipt = MessageReceipt.forMessage(messageId, recipientId, now);
            receipt.markRead(now);
            return receiptRepository.save(receipt);
        }

        receipt.markDelivered(now);
        receipt.markRead(now);
        return receipt;
    }

    public MessageReceiptUpdate toUpdate(MessageReceipt receipt, Long conversationId, MessageReceiptUpdate.State state) {
        return state == MessageReceiptUpdate.State.READ
                ? MessageReceiptUpdate.read(receipt, conversationId)
                : MessageReceiptUpdate.delivered(receipt, conversationId);
    }
}
