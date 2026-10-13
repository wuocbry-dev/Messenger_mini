package com.team503.messenger.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Editable fields of the current user's profile. Any field may be omitted to keep its current value.")
public record UpdateProfileRequest(

        @Schema(
                description = "New display name (1-100 chars).",
                example = "Alice the Great",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(min = 1, max = 100)
        String displayName,

        @Schema(
                description = "R2 object key of an uploaded avatar image. Pass an empty string to clear.",
                example = "messenger/avatars/42.png",
                maxLength = 512,
                nullable = true
        )
        @Size(max = 512)
        String avatarObjectKey
) {
}
