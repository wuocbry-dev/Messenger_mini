package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Client-controlled payload for STOMP /app/chat.send.
 * <p>
 * Only the client-controlled fields live here: conversationId, type, content.
 * Server fields (id, senderId, createdAt, delivery metadata) are set by the
 * backend from the authenticated principal and the database.
 */
public record SendChatMessageRequest(
        @NotNull
        Long conversationId,

        @NotBlank
        @Size(max = 20)
        String type,

        @NotBlank
        String content
) {

    public Message.Type toTypeOrDefault() {
        if (type == null) {
            return Message.Type.TEXT;
        }
        try {
            return Message.Type.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Message.Type.TEXT;
        }
    }
}
