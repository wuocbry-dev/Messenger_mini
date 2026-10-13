package com.team503.messenger.file.dto;

import com.team503.messenger.file.entity.StoredFile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * Response for {@code POST /api/v1/files}. Includes the presigned URL the
 * client will PUT the binary to. The presigned URL is time-limited and must
 * never be persisted.
 */
@Schema(description = "Result of a presigned upload. PUT the file binary to uploadUrl before it expires.")
public record UploadFileResponse(

        @Schema(description = "Internal file id to use when referencing this file (e.g. when sending an attachment).", example = "123")
        Long fileId,

        @Schema(description = "Server-generated R2 object key. Persisted in MySQL.", example = "messenger/2026/10/13/123-sunset.png")
        String objectKey,

        @Schema(description = "Original filename from the uploader.", example = "sunset.png")
        String originalName,

        @Schema(description = "MIME type as declared by the client and validated against the allow-list.", example = "image/png")
        String mimeType,

        @Schema(description = "File size in bytes.", example = "524288")
        Long sizeBytes,

        @Schema(description = "Client-supplied SHA-256 (echoed back).", example = "9b71d224...3ca7", nullable = true)
        String checksumSha256,

        @Schema(description = "R2 ETag returned after the upload finalises. Null until PUT completes.", example = "\"9b71d224...\"", nullable = true)
        String etag,

        @Schema(description = "When the file metadata row was created (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime createdAt,

        @Schema(description = "Presigned R2 PUT URL. Do not persist.")
        URI uploadUrl,

        @Schema(description = "Seconds until the presigned URL stops accepting uploads.", example = "900")
        long uploadUrlExpiresInSeconds
) {

    public static UploadFileResponse from(
            StoredFile file,
            URI uploadUrl,
            long uploadUrlExpiresInSeconds
    ) {
        return new UploadFileResponse(
                file.getId(),
                file.getObjectKey(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSizeBytes(),
                file.getChecksumSha256(),
                file.getEtag(),
                file.getCreatedAt(),
                uploadUrl,
                uploadUrlExpiresInSeconds
        );
    }
}
