package com.team503.messenger.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "";
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlDays = 30;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }

    public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }
}
