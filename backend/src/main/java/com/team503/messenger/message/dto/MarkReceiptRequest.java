package com.team503.messenger.message.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Client-controlled payload for STOMP {@code /app/receipts.mark}.
 * Client may only set the messageId and the new state. Server controls the
 * timestamp and verifies the recipient.
 */
public record MarkReceiptRequest(
        @NotNull
        Long messageId,

        @NotNull
        String state
) {

    public MessageReceiptUpdate.State toState() {
        try {
            return MessageReceiptUpdate.State.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
