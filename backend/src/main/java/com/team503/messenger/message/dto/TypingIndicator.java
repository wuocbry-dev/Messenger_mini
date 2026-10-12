package com.team503.messenger.message.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Realtime typing indicator. SenderId is server-derived from the authenticated
 * principal, never trusted from the client.
 */
public record TypingIndicator(
        @NotNull
        Long conversationId,

        @NotNull
        Long senderId,

        String senderUsername,
        boolean typing,
        LocalDateTime emittedAt
) {
}
