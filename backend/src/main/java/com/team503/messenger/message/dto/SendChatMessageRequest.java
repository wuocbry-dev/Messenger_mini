package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "STOMP payload for sending a chat message. Sender id is taken from the JWT principal.")
public record SendChatMessageRequest(

        @Schema(description = "Target conversation id.", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long conversationId,

        @Schema(
                description = "Message kind. Falls back to TEXT when missing or unrecognised.",
                example = "TEXT",
                allowableValues = {"TEXT", "IMAGE", "FILE", "SYSTEM"},
                maxLength = 20
        )
        @NotBlank
        @Size(max = 20)
        String type,

        @Schema(description = "Plain text body. For IMAGE / FILE messages this is the caption.", example = "Hello!")
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
