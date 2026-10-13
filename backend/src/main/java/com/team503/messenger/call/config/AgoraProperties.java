package com.team503.messenger.call.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agora")
public class AgoraProperties {

    /** Agora App ID (public). */
    private String appId;

    /** Agora App Certificate (backend-only, never exposed). */
    private String appCertificate;

    /** RTC token validity in seconds. */
    private long tokenTtlSeconds = 600;

    /** Seconds a call may ring before it is marked MISSED. */
    private long ringTimeoutSeconds = 45;

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

    public long getRingTimeoutSeconds() {
        return ringTimeoutSeconds;
    }

    public void setRingTimeoutSeconds(long ringTimeoutSeconds) {
        this.ringTimeoutSeconds = ringTimeoutSeconds;
    }
}
