package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Send a message that wraps an already-uploaded R2 file. Use POST /api/v1/files first to obtain fileId.")
public record SendAttachmentRequest(

        @Schema(
                description = "Conversation id. Must match the path variable of POST /conversations/{conversationId}/attachments.",
                example = "7",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        Long conversationId,

        @Schema(
                description = "File id returned by POST /api/v1/files.",
                example = "123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        Long fileId,

        /**
         * Either {@code IMAGE} or {@code FILE}. The client-controlled type is
         * validated against the file's MIME type server-side.
         */
        @Schema(
                description = "Attachment kind. Must be IMAGE or FILE.",
                example = "IMAGE",
                allowableValues = {"IMAGE", "FILE"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        String type,

        /**
         * Optional caption / alt text shown alongside the attachment.
         */
        @Schema(description = "Optional caption / alt text.", example = "Sunset at the lake", nullable = true)
        String content
) {

    public Message.Type toType() {
        try {
            return Message.Type.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
