package com.team503.messenger.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank
        @Size(min = 1, max = 100)
        String displayName,

        @Size(max = 512)
        String avatarObjectKey
) {
}
