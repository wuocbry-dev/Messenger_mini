package com.team503.messenger.user.dto;

import java.util.List;

/**
 * Lightweight user result for user-search / new-conversation UIs.
 * Does not expose email or status to protect privacy.
 */
public record UserSearchResult(
        Long id,
        String username,
        String displayName,
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
