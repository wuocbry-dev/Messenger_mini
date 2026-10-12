package com.team503.messenger.user.dto;

import com.team503.messenger.user.entity.User;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String avatarObjectKey,
        String status,
        LocalDateTime lastSeenAt,
        LocalDateTime createdAt,
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
