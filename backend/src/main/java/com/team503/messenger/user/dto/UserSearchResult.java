package com.team503.messenger.user.dto;

import com.team503.messenger.user.entity.User;

/**
 * Lightweight user result for user-search / new-conversation UIs.
 * Does not expose email or status to protect privacy.
 */
public record UserSearchResult(
        Long id,
        String username,
        String displayName,
        String avatarObjectKey,
        String avatarUrl
) {

    public static UserSearchResult from(User user, String avatarUrl) {
        return new UserSearchResult(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarObjectKey(),
                avatarUrl
        );
    }
}
