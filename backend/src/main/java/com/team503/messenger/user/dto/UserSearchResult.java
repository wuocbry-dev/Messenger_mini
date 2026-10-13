package com.team503.messenger.user.dto;

import com.team503.messenger.user.entity.User;
import java.time.LocalDateTime;

/**
 * Lightweight user result for the user directory and new-conversation UIs.
 * Does not expose email or account status to protect privacy.
 */
public record UserSearchResult(
        Long id,
        String username,
        String displayName,
        String avatarObjectKey,
        String avatarUrl,
        boolean online,
        LocalDateTime lastSeenAt
) {

    public static UserSearchResult from(User user, String avatarUrl, boolean online) {
        return new UserSearchResult(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarObjectKey(),
                avatarUrl,
                online,
                user.getLastSeenAt()
        );
    }
}
