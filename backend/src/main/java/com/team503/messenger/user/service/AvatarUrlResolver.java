package com.team503.messenger.user.service;

import com.team503.messenger.file.service.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a stored avatar object key into a short-lived presigned GET URL.
 * The URL is computed per response and never persisted; only the object key
 * lives in MySQL.
 */
@Component
public class AvatarUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(AvatarUrlResolver.class);

    private final ObjectStorageService storage;

    public AvatarUrlResolver(ObjectStorageService storage) {
        this.storage = storage;
    }

    /** Returns the presigned URL, or null when there is no avatar or storage is unavailable. */
    public String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            return storage.createDownloadUrl(objectKey).toString();
        } catch (RuntimeException e) {
            log.warn("Could not create avatar URL: {}", e.getMessage());
            return null;
        }
    }
}
