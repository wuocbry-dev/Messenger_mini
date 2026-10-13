package com.team503.messenger.user.dto;

import com.team503.messenger.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Public user profile returned by the API. Never contains the password hash.")
public record UserResponse(

        @Schema(description = "Internal numeric user id.", example = "42")
        Long id,

        @Schema(description = "Unique username.", example = "alice")
        String username,

        @Schema(description = "Account email address.", example = "alice@example.com")
        String email,

        @Schema(description = "Display name shown in chats.", example = "Alice")
        String displayName,

        @Schema(
                description = "R2 object key for the avatar image, or null if no avatar is set.",
                example = "messenger/avatars/42.png",
                nullable = true
        )
        String avatarObjectKey,

        @Schema(
                description = "Account status.",
                example = "ACTIVE",
                allowableValues = {"ACTIVE", "DISABLED"}
        )
        String status,

        @Schema(description = "Last time the user was seen online.", example = "2026-10-13T11:24:00", nullable = true)
        LocalDateTime lastSeenAt,

        @Schema(description = "Account creation timestamp (UTC).", example = "2026-09-01T08:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Last profile update timestamp (UTC).", example = "2026-10-12T14:30:00")
        LocalDateTime updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarObjectKey(),
                user.getStatus(),
                user.getLastSeenAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
