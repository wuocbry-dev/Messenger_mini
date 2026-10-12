package com.team503.messenger.call.dto;

public record CallTokenResponse(
        Long callId,
        String agoraChannelName,
        String token,
        String appId,
        int expiresInSeconds
) {
}
