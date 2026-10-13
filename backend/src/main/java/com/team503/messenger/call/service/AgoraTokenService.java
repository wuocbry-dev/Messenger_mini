package com.team503.messenger.call.service;

public interface AgoraTokenService {

    /**
     * Builds a short-lived RTC token bound to the channel and numeric uid.
     * Tokens are never persisted.
     */
    String generateRtcToken(String channelName, long userId, int expiresInSeconds);
}
