package com.team503.messenger.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Realtime typing indicator. SenderId is server-derived from the authenticated
 * principal, never trusted from the client.
 */
@Schema(description = "Realtime typing event pushed over STOMP.")
public record TypingIndicator(

        @Schema(description = "Conversation id the user is typing in.", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long conversationId,

        @Schema(description = "Server-derived sender user id.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long senderId,

        @Schema(description = "Server-derived sender username.", example = "alice")
        String senderUsername,

        @Schema(description = "True while the user is actively typing.", example = "true")
        boolean typing,

        @Schema(description = "Server-emitted timestamp (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime emittedAt
) {
}
