package com.team503.messenger.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.storage.r2")
@Validated
public class R2Properties {

    /**
     * Cloudflare R2 endpoint URL, e.g. https://&lt;accountid&gt;.r2.cloudflarestorage.com
     */
    private String endpoint;

    private String accessKeyId;

    private String secretAccessKey;

    private String bucketName;

    /**
     * Validity window for presigned URLs.
     */
    private long presignedTtlSeconds = 900;

    /**
     * Path prefix for all uploaded objects, e.g. {@code messenger/}.
     */
    private String keyPrefix = "messenger/";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public long getPresignedTtlSeconds() {
        return presignedTtlSeconds;
    }

    public void setPresignedTtlSeconds(long presignedTtlSeconds) {
        this.presignedTtlSeconds = presignedTtlSeconds;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
