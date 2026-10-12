package com.team503.messenger.message.dto;

import com.team503.messenger.message.entity.Message;
import jakarta.validation.constraints.NotNull;

public record SendAttachmentRequest(
        @NotNull
        Long conversationId,

        @NotNull
        Long fileId,

        /**
         * Either {@code IMAGE} or {@code FILE}. The client-controlled type is
         * validated against the file's MIME type server-side.
         */
        @NotNull
        String type,

        /**
         * Optional caption / alt text shown alongside the attachment.
         */
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
