package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Message returned by REST and pushed over STOMP. Includes sender info, receipts and attachments.")
public record MessageResponse(

        @Schema(description = "Message id.", example = "501")
        Long id,

        @Schema(description = "Owning conversation id.", example = "7")
        Long conversationId,

        @Schema(description = "User id of the sender.", example = "1")
        Long senderId,

        @Schema(description = "Username of the sender.", example = "alice")
        String senderUsername,

        @Schema(description = "Display name of the sender.", example = "Alice")
        String senderDisplayName,

        @Schema(
                description = "Message kind.",
                example = "TEXT",
                allowableValues = {"TEXT", "IMAGE", "FILE", "SYSTEM"}
        )
        String type,

        @Schema(description = "Plain text body. Empty for pure attachment messages.", example = "Hi!", nullable = true)
        String content,

        @Schema(description = "When the message was created (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime createdAt,

        @Schema(description = "Per-user delivered/read receipts, if the requester is the sender. Empty otherwise.")
        List<MessageReceiptResponse> receipts,

        @Schema(description = "Attached files. Empty for TEXT messages.")
        List<MessageAttachmentResponse> attachments
) {

    public static MessageResponse from(Message message, String senderUsername, String senderDisplayName, List<MessageReceiptResponse> receipts) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                senderUsername,
                senderDisplayName,
                message.getType(),
                message.getContent(),
                message.getCreatedAt(),
                receipts,
                List.of()
        );
    }
}
