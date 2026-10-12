package com.team503.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(max = 255)
        String usernameOrEmail,

        @NotBlank
        @Size(max = 72)
        String password
) {
}
