package com.team503.messenger.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Client-controlled payload for STOMP {@code /app/typing}. Client only sets
 * conversationId and the desired state (true/false). Server sets senderId and
 * timestamp.
 */
@Schema(description = "STOMP payload for reporting typing state on a conversation.")
public record TypingRequest(

        @Schema(description = "Conversation the user is typing in.", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long conversationId,

        @Schema(description = "True while the user is actively typing, false when they stop.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean typing
) {
}
