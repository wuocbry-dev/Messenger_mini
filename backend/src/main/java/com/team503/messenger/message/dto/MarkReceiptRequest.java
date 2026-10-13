package com.team503.messenger.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Client-controlled payload for STOMP {@code /app/receipts.mark}.
 * Client may only set the messageId and the new state. Server controls the
 * timestamp and verifies the recipient.
 */
@Schema(description = "STOMP payload for marking a message as DELIVERED or READ.")
public record MarkReceiptRequest(

        @Schema(description = "Id of the message the receipt is for.", example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long messageId,

        @Schema(
                description = "New receipt state.",
                example = "READ",
                allowableValues = {"DELIVERED", "READ"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
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
