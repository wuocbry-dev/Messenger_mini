package com.team503.messenger.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.r2")
public record R2Properties(
        String endpoint,
        String accessKeyId,
        String secretAccessKey,
        String bucketName
) {
}
