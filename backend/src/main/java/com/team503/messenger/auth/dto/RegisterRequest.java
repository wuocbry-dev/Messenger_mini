package com.team503.messenger.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New account registration payload.")
public record RegisterRequest(

        @Schema(
                description = "Unique username (3-50 chars, case-insensitive).",
                example = "alice",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @Schema(
                description = "Unique email address.",
                example = "alice@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Schema(
                description = "Password (8-72 chars).",
                example = "S3cret!",
                minLength = 8,
                maxLength = 72,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @Schema(
                description = "Display name shown to other users.",
                example = "Alice",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 100)
        String displayName
) {
}
