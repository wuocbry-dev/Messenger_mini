package com.team503.messenger.file.service;

import com.team503.messenger.file.config.R2Properties;
import com.team503.messenger.file.dto.FileResponse;
import com.team503.messenger.file.dto.UploadFileRequest;
import com.team503.messenger.file.dto.UploadFileResponse;
import com.team503.messenger.file.entity.StoredFile;
import com.team503.messenger.file.exception.FileException;
import com.team503.messenger.file.repository.StoredFileRepository;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates file metadata persistence with the underlying
 * {@link ObjectStorageService}. The R2 secret is held by the storage
 * implementation; this service never sees it.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * Maximum upload size: 50 MB. The presigned upload URL is generated with
     * the same constraint via the {@code Content-Length} header the client
     * must send.
     */
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    private final StoredFileRepository repository;
    private final ObjectStorageService storage;
    private final R2Properties properties;
    private final Clock clock;

    public FileService(
            StoredFileRepository repository,
            ObjectStorageService storage,
            R2Properties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Allocate an object key and return a presigned PUT URL plus the
     * pre-persisted metadata row. The DB row is created with
     * {@code sizeBytes} and {@code etag} as declared by the client; both can
     * be reconciled later (out of scope for this task).
     */
    @Transactional
    public UploadFileResponse prepareUpload(Long uploaderId, UploadFileRequest request) {
        if (!request.isAllowedMimeType()) {
            throw new FileException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_MIME_TYPE",
                    "MIME type '" + request.mimeType() + "' is not allowed"
            );
        }
        if (request.sizeBytes() > MAX_FILE_SIZE_BYTES) {
            throw new FileException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "File size exceeds the maximum of " + MAX_FILE_SIZE_BYTES + " bytes"
            );
        }

        String objectKey = generateObjectKey(uploaderId, request.originalName());

        // We pre-persist the metadata row so the client can later attach the
        // file to a message via this id. The DB row is the source of truth;
        // the R2 upload is a follow-up step performed by the client.
        StoredFile file = StoredFile.create(
                uploaderId,
                objectKey,
                sanitizeOriginalName(request.originalName()),
                request.mimeType().toLowerCase(),
                request.sizeBytes(),
                request.checksumSha256(),
                null, // ETag will be filled in once the client uploads.
                LocalDateTime.now(clock)
        );
        StoredFile saved = repository.save(file);

        URI uploadUrl = storage.createUploadUrl(objectKey, request.mimeType());
        return UploadFileResponse.from(saved, uploadUrl, properties.getPresignedTtlSeconds());
    }

    /**
     * Return metadata for a stored file along with a presigned GET URL.
     */
    @Transactional(readOnly = true)
    public FileResponse getFile(Long fileId) {
        StoredFile file = repository.findById(fileId)
                .orElseThrow(() -> new FileException(
                        HttpStatus.NOT_FOUND,
                        "FILE_NOT_FOUND",
                        "File not found"
                ));
        URI downloadUrl = storage.createDownloadUrl(file.getObjectKey());
        return FileResponse.from(file, downloadUrl, properties.getPresignedTtlSeconds());
    }

    /**
     * Build a deterministic but non-guessable object key. We use a
     * per-user prefix so ACLs and rate limits can later be scoped to a
     * sub-key.
     */
    String generateObjectKey(Long uploaderId, String originalName) {
        String extension = extractExtension(originalName);
        String random = UUID.randomUUID().toString().replace("-", "");
        return properties.getKeyPrefix() + "u" + uploaderId + "/" + random + extension;
    }

    private static String extractExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot).toLowerCase();
        // Reject anything weird in the extension.
        if (!ext.matches("\\.[a-z0-9]{1,8}")) {
            return "";
        }
        return ext;
    }

    private static String sanitizeOriginalName(String originalName) {
        if (originalName == null) {
            return "file";
        }
        // Strip path separators; keep the basename only.
        String basename = originalName;
        int slash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (slash >= 0) {
            basename = basename.substring(slash + 1);
        }
        if (basename.isBlank()) {
            return "file";
        }
        return basename.length() > 255 ? basename.substring(0, 255) : basename;
    }
}
