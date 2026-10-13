package com.team503.messenger.user.service;

import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.file.exception.FileException;
import com.team503.messenger.file.repository.StoredFileRepository;
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
    private final StoredFileRepository storedFileRepository;
    private final AvatarUrlResolver avatarUrlResolver;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            StoredFileRepository storedFileRepository,
            AvatarUrlResolver avatarUrlResolver,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.storedFileRepository = storedFileRepository;
        this.avatarUrlResolver = avatarUrlResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .map(this::toResponse)
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
        if (avatarObjectKey != null && !avatarObjectKey.equals(user.getAvatarObjectKey())) {
            requireOwnImage(userId, avatarObjectKey);
        }

        user.updateProfile(request.displayName(), avatarObjectKey, LocalDateTime.now(clock));
        return toResponse(user);
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
                .map(this::toSearchResult)
                .toList();
    }

    /**
     * Get all active users except the current user.
     * Used by the user directory page.
     */
    @Transactional(readOnly = true)
    public List<UserSearchResult> getAllActiveUsers(Long currentUserId) {
        return userRepository.findAllActiveUsersExcept(currentUserId).stream()
                .map(this::toSearchResult)
                .toList();
    }

    /** An avatar must be an image the user uploaded themselves. */
    private void requireOwnImage(Long userId, String objectKey) {
        boolean valid = storedFileRepository.findByObjectKey(objectKey)
                .filter(f -> f.getUploaderId().equals(userId))
                .filter(f -> f.getMimeType() != null && f.getMimeType().startsWith("image/"))
                .isPresent();
        if (!valid) {
            throw new FileException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AVATAR",
                    "Avatar must be an image you uploaded"
            );
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, avatarUrlResolver.resolve(user.getAvatarObjectKey()));
    }

    private UserSearchResult toSearchResult(User user) {
        return UserSearchResult.from(user, avatarUrlResolver.resolve(user.getAvatarObjectKey()));
    }
}
