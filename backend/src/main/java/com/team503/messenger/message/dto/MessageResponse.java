package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.Message;
import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderUsername,
        String senderDisplayName,
        String type,
        String content,
        LocalDateTime createdAt,
        List<MessageReceiptResponse> receipts,
        List<MessageAttachmentResponse> attachments,
        /** Set only for CALL messages. */
        MessageCallResponse call
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
                List.of(),
                null
        );
    }
}
