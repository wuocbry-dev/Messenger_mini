package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.MessageReceipt;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Realtime receipt update published to {@code /user/{username}/queue/receipts}.
 */
@Schema(description = "Receipt update pushed over STOMP when a recipient marks a message DELIVERED or READ.")
public record MessageReceiptUpdate(

        @Schema(description = "Message id the receipt is for.", example = "501")
        Long messageId,

        @Schema(description = "Owning conversation id.", example = "7")
        Long conversationId,

        @Schema(description = "Recipient user id that produced the receipt.", example = "42")
        Long userId,

        @Schema(
                description = "Receipt state.",
                example = "READ",
                allowableValues = {"DELIVERED", "READ"}
        )
        String state,

        @Schema(description = "Delivery timestamp. Null if not delivered yet.", example = "2026-10-13T11:24:05", nullable = true)
        LocalDateTime deliveredAt,

        @Schema(description = "Read timestamp. Null if not read yet.", example = "2026-10-13T11:25:10", nullable = true)
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
