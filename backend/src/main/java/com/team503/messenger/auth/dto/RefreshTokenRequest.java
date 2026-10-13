package com.team503.messenger.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh or revoke a JWT refresh token.")
public record RefreshTokenRequest(

        @Schema(
                description = "Refresh token previously issued by /login or /refresh.",
                example = "d4f1...e0a9",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String refreshToken
) {
}
