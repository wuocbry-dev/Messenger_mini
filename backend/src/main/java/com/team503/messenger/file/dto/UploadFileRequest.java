package com.team503.messenger.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Client-controlled payload for {@code POST /api/v1/files} (presigned upload).
 * The server generates the object key and the presigned URL.
 */
public record UploadFileRequest(
        @NotBlank
        @Size(max = 255)
        String originalName,

        @NotBlank
        @Size(max = 150)
        String mimeType,

        @NotNull
        @Positive
        Long sizeBytes,

        String checksumSha256
) {

    /**
     * Server-side allow-list of MIME types. Anything outside this set is
     * rejected. Images and a small set of common document types are allowed.
     */
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            // Images
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            // Documents
            "application/pdf",
            "text/plain",
            "application/zip"
    );

    public boolean isAllowedMimeType() {
        return mimeType != null && ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase());
    }
}
