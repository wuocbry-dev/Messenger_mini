package com.team503.messenger.user.service;

import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.user.dto.UpdateProfileRequest;
import com.team503.messenger.user.dto.UserResponse;
import com.team503.messenger.user.dto.UserSearchResult;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .map(UserResponse::from)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "CURRENT_USER_NOT_FOUND",
                        "Current user is not available"
                ));
    }

    @Transactional
    public UserResponse updateCurrentUserProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "CURRENT_USER_NOT_FOUND",
                        "Current user is not available"
                ));

        String avatarObjectKey = request.avatarObjectKey();
        if (avatarObjectKey != null && avatarObjectKey.isBlank()) {
            avatarObjectKey = null;
        }

        user.updateProfile(request.displayName(), avatarObjectKey, LocalDateTime.now(clock));
        return UserResponse.from(user);
    }

    /**
     * Search active users by username or display name.
     * Returns lightweight results (no email/status) for privacy.
     */
    @Transactional(readOnly = true)
    public List<UserSearchResult> searchUsers(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return userRepository.searchActiveUsers(query.trim()).stream()
                .map(UserSearchResult::from)
                .toList();
    }
}
