package com.team503.messenger.file.dto;

import com.team503.messenger.file.entity.StoredFile;
import java.net.URI;
import java.time.LocalDateTime;

/**
 * Response for {@code POST /api/v1/files}. Includes the presigned URL the
 * client will PUT the binary to. The presigned URL is time-limited and must
 * never be persisted.
 */
public record UploadFileResponse(
        Long fileId,
        String objectKey,
        String originalName,
        String mimeType,
        Long sizeBytes,
        String checksumSha256,
        String etag,
        LocalDateTime createdAt,
        URI uploadUrl,
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
