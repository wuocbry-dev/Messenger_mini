package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.MessageAttachment;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Attachment metadata attached to a message.")
public record MessageAttachmentResponse(

        @Schema(description = "Attachment id.", example = "91")
        Long id,

        @Schema(description = "Underlying stored file id (see /api/v1/files).", example = "123")
        Long fileId,

        @Schema(
                description = "Attachment kind. Mirrors the message type: IMAGE or FILE.",
                example = "IMAGE",
                allowableValues = {"IMAGE", "FILE"}
        )
        String attachmentType,

        @Schema(description = "MIME type detected at upload.", example = "image/png")
        String mimeType,

        @Schema(description = "Original filename from the uploader.", example = "sunset.png")
        String originalName,

        @Schema(description = "File size in bytes.", example = "524288")
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
