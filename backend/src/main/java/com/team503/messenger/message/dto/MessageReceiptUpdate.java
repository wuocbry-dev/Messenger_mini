package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.MessageReceipt;
import java.time.LocalDateTime;

/**
 * Realtime receipt update published to {@code /user/{username}/queue/receipts}.
 */
public record MessageReceiptUpdate(
        Long messageId,
        Long conversationId,
        Long userId,
        String state,
        LocalDateTime deliveredAt,
        LocalDateTime readAt
) {

    public enum State {
        DELIVERED,
        READ
    }

    public static MessageReceiptUpdate delivered(MessageReceipt receipt, Long conversationId) {
        return new MessageReceiptUpdate(
                receipt.getMessageId(),
                conversationId,
                receipt.getUserId(),
                State.DELIVERED.name(),
                receipt.getDeliveredAt(),
                receipt.getReadAt()
        );
    }

    public static MessageReceiptUpdate read(MessageReceipt receipt, Long conversationId) {
        return new MessageReceiptUpdate(
                receipt.getMessageId(),
                conversationId,
                receipt.getUserId(),
                State.READ.name(),
                receipt.getDeliveredAt(),
                receipt.getReadAt()
        );
    }
}
