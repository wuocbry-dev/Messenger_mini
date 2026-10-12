package com.team503.messenger.message.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Client-controlled payload for STOMP {@code /app/typing}. Client only sets
 * conversationId and the desired state (true/false). Server sets senderId and
 * timestamp.
 */
public record TypingRequest(
        @NotNull
        Long conversationId,

        @NotNull
        Boolean typing
) {
}
