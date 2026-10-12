package com.team503.messenger.file.config;

import com.team503.messenger.file.service.NoopObjectStorageService;
import com.team503.messenger.file.service.ObjectStorageService;
import com.team503.messenger.file.service.R2StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2Config {

    private static final Logger log = LoggerFactory.getLogger(R2Config.class);

    @Bean
    ObjectStorageService objectStorageService(R2Properties properties) {
        if (isBlank(properties.getEndpoint())
                || isBlank(properties.getAccessKeyId())
                || isBlank(properties.getSecretAccessKey())
                || isBlank(properties.getBucketName())) {
            log.warn("R2 credentials are not configured; falling back to NoopObjectStorageService. "
                    + "File uploads will be unavailable until R2 is configured.");
            return new NoopObjectStorageService();
        }
        return new R2StorageService(properties);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
