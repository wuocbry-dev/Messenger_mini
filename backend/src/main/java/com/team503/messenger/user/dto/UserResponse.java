package com.team503.messenger.user.dto;

import com.team503.messenger.user.entity.User;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String avatarObjectKey,
        /** Short-lived presigned URL for the avatar; null when there is none. Never persisted. */
        String avatarUrl,
        String status,
        LocalDateTime lastSeenAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, String avatarUrl) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarObjectKey(),
                avatarUrl,
                user.getStatus(),
                user.getLastSeenAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
