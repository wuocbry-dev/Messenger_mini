package com.team503.messenger.file.service;

import java.io.InputStream;
import java.net.URI;

/**
 * No-op implementation used when R2 credentials are not configured. Upload
 * requests will fail; the rest of the application continues to start. This is
 * useful for local dev where R2 may not be available.
 */
public class NoopObjectStorageService implements ObjectStorageService {

    @Override
    public String putObject(String objectKey, InputStream content, long contentLength, String contentType) {
        throw new UnsupportedOperationException("R2 is not configured");
    }

    @Override
    public URI createDownloadUrl(String objectKey) {
        throw new UnsupportedOperationException("R2 is not configured");
    }

    @Override
    public URI createUploadUrl(String objectKey, String contentType) {
        throw new UnsupportedOperationException("R2 is not configured");
    }

    @Override
    public void deleteObject(String objectKey) {
        // no-op
    }

    @Override
    public boolean objectExists(String objectKey) {
        return false;
    }
}
