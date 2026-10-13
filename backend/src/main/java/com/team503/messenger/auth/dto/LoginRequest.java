package com.team503.messenger.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credentials for login. Accepts either username or email.")
public record LoginRequest(

        @Schema(
                description = "Username or email of the account.",
                example = "alice",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 255)
        String usernameOrEmail,

        @Schema(
                description = "Account password (plain text, sent over HTTPS).",
                example = "S3cret!",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 72)
        String password
) {
}
