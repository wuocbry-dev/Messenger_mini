package com.team503.messenger.call.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.agora")
@Validated
public class AgoraProperties {

    /**
     * Agora App ID (public).
     */
    private String appId;

    /**
     * Agora App Certificate (backend-only, never exposed to frontend).
     */
    private String appCertificate;

    /**
     * Token validity window in seconds.
     */
    private long tokenTtlSeconds = 600;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppCertificate() {
        return appCertificate;
    }

    public void setAppCertificate(String appCertificate) {
        this.appCertificate = appCertificate;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }
}
