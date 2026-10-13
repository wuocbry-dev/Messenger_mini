package com.team503.messenger.call.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Short-lived Agora RTC token. The token must not be persisted.")
public record CallTokenResponse(

        @Schema(description = "Call id this token is bound to.", example = "88")
        Long callId,

        @Schema(description = "Agora channel name to join.", example = "c-7-1-42-1730000000")
        String agoraChannelName,

        @Schema(description = "Agora RTC token (JWT-style).", example = "006...AgEA")
        String token,

        @Schema(description = "Agora App ID this token was signed for.", example = "813905d184694b218dee5e56fd7d530d")
        String appId,

        @Schema(description = "Seconds until the token expires. Re-issue before this elapses.", example = "600")
        int expiresInSeconds
) {
}
