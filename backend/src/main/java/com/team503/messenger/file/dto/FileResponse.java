package com.team503.messenger.file.dto;

import com.team503.messenger.file.entity.StoredFile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.LocalDateTime;

@Schema(description = "File metadata plus a short-lived presigned download URL.")
public record FileResponse(

        @Schema(description = "Internal file id.", example = "123")
        Long fileId,

        @Schema(description = "R2 object key.", example = "messenger/2026/10/13/123-sunset.png")
        String objectKey,

        @Schema(description = "Original filename.", example = "sunset.png")
        String originalName,

        @Schema(description = "MIME type.", example = "image/png")
        String mimeType,

        @Schema(description = "File size in bytes.", example = "524288")
        Long sizeBytes,

        @Schema(description = "Client-supplied SHA-256.", example = "9b71d224...3ca7", nullable = true)
        String checksumSha256,

        @Schema(description = "R2 ETag after finalisation.", example = "\"9b71d224...\"", nullable = true)
        String etag,

        @Schema(description = "User id that uploaded the file.", example = "1")
        Long uploaderId,

        @Schema(description = "Upload timestamp (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime createdAt,

        @Schema(description = "Presigned R2 GET URL. Do not persist.")
        URI downloadUrl,

        @Schema(description = "Seconds until the download URL expires.", example = "900")
        long downloadUrlExpiresInSeconds
) {

    public static FileResponse from(
            StoredFile file,
            URI downloadUrl,
            long expiresInSeconds
    ) {
        return new FileResponse(
                file.getId(),
                file.getObjectKey(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSizeBytes(),
                file.getChecksumSha256(),
                file.getEtag(),
                file.getUploaderId(),
                file.getCreatedAt(),
                downloadUrl,
                expiresInSeconds
        );
    }
}
