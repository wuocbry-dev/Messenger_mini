package com.team503.messenger.file.service;

import com.team503.messenger.file.exception.FileException;
import java.io.InputStream;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * No-op implementation used when R2 credentials are not configured. Upload
 * requests will fail; the rest of the application continues to start. This is
 * useful for local dev where R2 may not be available.
 */
public class NoopObjectStorageService implements ObjectStorageService {

    @Override
    public String putObject(String objectKey, InputStream content, long contentLength, String contentType) {
        throw storageUnavailable();
    }

    @Override
    public URI createDownloadUrl(String objectKey) {
        throw storageUnavailable();
    }

    @Override
    public URI createUploadUrl(String objectKey, String contentType) {
        throw storageUnavailable();
    }

    @Override
    public void deleteObject(String objectKey) {
        // no-op
    }

    @Override
    public boolean objectExists(String objectKey) {
        return false;
    }

    private static FileException storageUnavailable() {
        return new FileException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_UNAVAILABLE",
                "File storage (R2) is not configured"
        );
    }
}
