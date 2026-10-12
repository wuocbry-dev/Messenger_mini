package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.MessageAttachment;

public record MessageAttachmentResponse(
        Long id,
        Long fileId,
        String attachmentType,
        String mimeType,
        String originalName,
        Long sizeBytes
) {

    public static MessageAttachmentResponse from(MessageAttachment attachment, String mimeType, String originalName, Long sizeBytes) {
        return new MessageAttachmentResponse(
                attachment.getId(),
                attachment.getFileId(),
                attachment.getAttachmentType(),
                mimeType,
                originalName,
                sizeBytes
        );
    }
}
