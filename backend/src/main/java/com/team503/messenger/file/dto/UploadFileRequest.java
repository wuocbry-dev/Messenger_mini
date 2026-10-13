package com.team503.messenger.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Client-controlled payload for {@code POST /api/v1/files} (presigned upload).
 * The server generates the object key and the presigned URL.
 */
@Schema(description = "Declare a file to upload. The server returns a presigned R2 PUT URL; the client uploads the binary directly.")
public record UploadFileRequest(

        @Schema(description = "Original filename from the user.", example = "sunset.png", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 255)
        String originalName,

        @Schema(
                description = "MIME type. Must be in the server allow-list (images, pdf, txt, zip).",
                example = "image/png",
                maxLength = 150,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 150)
        String mimeType,

        @Schema(description = "Exact file size in bytes.", example = "524288", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Positive
        Long sizeBytes,

        @Schema(description = "Optional SHA-256 of the file body for integrity check.", example = "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca7", nullable = true)
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
