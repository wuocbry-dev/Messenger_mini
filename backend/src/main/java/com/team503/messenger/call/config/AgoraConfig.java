package com.team503.messenger.call.config;

import com.team503.messenger.call.service.AgoraTokenService;
import com.team503.messenger.call.service.DefaultAgoraTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgoraProperties.class)
public class AgoraConfig {

    private static final Logger log = LoggerFactory.getLogger(AgoraConfig.class);

    @Bean
    AgoraTokenService agoraTokenService(AgoraProperties properties) {
        logConfiguration(properties);
        return new DefaultAgoraTokenService(properties);
    }

    /** Startup summary. The App ID is public; the certificate is only described, never printed. */
    private static void logConfiguration(AgoraProperties properties) {
        String appId = properties.getAppId();
        String cert = properties.getAppCertificate();
        log.info("[agora] config: appId={} ({}), appCertificate={}, tokenTtl={}s",
                appId == null || appId.isBlank() ? "<missing>" : appId,
                describe(appId),
                cert == null || cert.isBlank() ? "<missing>" : "<set>",
                properties.getTokenTtlSeconds());
        if (cert != null && !cert.isBlank()) {
            log.info("[agora] appCertificate: {}", describe(cert));
        }
        if (appId != null && appId.equalsIgnoreCase(cert)) {
            log.warn("[agora] App ID and App Certificate are identical; one of them was copied wrong");
        }
    }

    private static String describe(String value) {
        if (value == null || value.isBlank()) {
            return "empty";
        }
        boolean hex32 = value.matches("^[0-9a-fA-F]{32}$");
        return "length=" + value.length() + (hex32 ? ", 32-hex ok" : ", NOT 32-hex");
    }
}
