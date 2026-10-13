package com.team503.messenger.auth.dto;

import com.team503.messenger.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication result with JWT tokens and the current user profile.")
public record AuthResponse(

        @Schema(
                description = "Short-lived JWT access token. Send as `Authorization: Bearer <token>`.",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        )
        String accessToken,

        @Schema(
                description = "Long-lived refresh token used at /api/v1/auth/refresh.",
                example = "d4f1...e0a9"
        )
        String refreshToken,

        @Schema(
                description = "Token type for the Authorization header.",
                example = "Bearer",
                allowableValues = {"Bearer"}
        )
        String tokenType,

        @Schema(
                description = "Access token lifetime in seconds.",
                example = "900"
        )
        long expiresIn,

        @Schema(description = "The freshly authenticated or created user profile.")
        UserResponse user
) {
}
