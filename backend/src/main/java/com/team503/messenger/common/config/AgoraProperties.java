package com.team503.messenger.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agora")
public record AgoraProperties(
        String appId,
        String appCertificate
) {
}
