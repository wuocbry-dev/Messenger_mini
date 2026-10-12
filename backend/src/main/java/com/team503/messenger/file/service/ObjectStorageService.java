package com.team503.messenger.file.service;

import java.net.URI;

/**
 * Abstraction over object storage so the rest of the codebase can be unit
 * tested without R2.
 */
public interface ObjectStorageService {

    /**
     * Persist the given stream under {@code objectKey}. Returns the ETag if
     * the storage backend provides one, or null otherwise. Implementations
     * must reject unknown object keys (the caller controls the key).
     */
    String putObject(String objectKey, java.io.InputStream content, long contentLength, String contentType);

    /**
     * Create a time-limited URL for downloading the object. Must NOT be
     * persisted in MySQL.
     */
    URI createDownloadUrl(String objectKey);

    /**
     * Create a time-limited URL for uploading an object. The client will PUT
     * the file content directly to this URL.
     */
    URI createUploadUrl(String objectKey, String contentType);

    /**
     * Remove an object. Idempotent: deleting a missing object is a no-op.
     */
    void deleteObject(String objectKey);

    /**
     * Whether the given key exists in the bucket.
     */
    boolean objectExists(String objectKey);
}
