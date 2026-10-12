package com.team503.messenger.file.dto;

import com.team503.messenger.file.entity.StoredFile;
import java.net.URI;
import java.time.LocalDateTime;

public record FileResponse(
        Long fileId,
        String objectKey,
        String originalName,
        String mimeType,
        Long sizeBytes,
        String checksumSha256,
        String etag,
        Long uploaderId,
        LocalDateTime createdAt,
        URI downloadUrl,
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
