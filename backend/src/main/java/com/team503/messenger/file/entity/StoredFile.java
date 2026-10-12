package com.team503.messenger.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "uploader_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long uploaderId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", columnDefinition = "CHAR(64)")
    private String checksumSha256;

    @Column(name = "etag", length = 255)
    private String etag;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    protected StoredFile() {
    }

    private StoredFile(
            Long uploaderId,
            String objectKey,
            String originalName,
            String mimeType,
            Long sizeBytes,
            String checksumSha256,
            String etag,
            LocalDateTime createdAt
    ) {
        this.uploaderId = uploaderId;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.etag = etag;
        this.createdAt = createdAt;
    }

    public static StoredFile create(
            Long uploaderId,
            String objectKey,
            String originalName,
            String mimeType,
            Long sizeBytes,
            String checksumSha256,
            String etag,
            LocalDateTime createdAt
    ) {
        return new StoredFile(
                uploaderId, objectKey, originalName, mimeType, sizeBytes,
                checksumSha256, etag, createdAt
        );
    }

    public Long getId() {
        return id;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public String getEtag() {
        return etag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
