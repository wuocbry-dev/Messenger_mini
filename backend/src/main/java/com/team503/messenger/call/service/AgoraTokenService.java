package com.team503.messenger.call.service;

public interface AgoraTokenService {

    String generateRtcToken(String channelName, long userId, int expiresInSeconds);
}
