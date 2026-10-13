package com.team503.messenger.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight user result for user-search / new-conversation UIs.
 * Does not expose email or status to protect privacy.
 */
@Schema(description = "Lightweight user row for search results. Email and status are intentionally omitted.")
public record UserSearchResult(

        @Schema(description = "Internal numeric user id.", example = "42")
        Long id,

        @Schema(description = "Unique username.", example = "alice")
        String username,

        @Schema(description = "Display name.", example = "Alice")
        String displayName,

        @Schema(
                description = "R2 object key for the avatar, or null if no avatar is set.",
                example = "messenger/avatars/42.png",
                nullable = true
        )
        String avatarObjectKey
) {

    public static UserSearchResult from(com.team503.messenger.user.entity.User user) {
        return new UserSearchResult(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarObjectKey()
        );
    }
}
