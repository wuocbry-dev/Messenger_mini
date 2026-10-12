package com.team503.messenger.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.user.dto.UpdateProfileRequest;
import com.team503.messenger.user.dto.UserResponse;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        userService = new UserService(userRepository, CLOCK);
    }

    @Test
    void getCurrentUserReturnsProfileForActiveUser() {
        User user = activeUser(1L, "quocbry", "Quoc Bry");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("quocbry");
        assertThat(response.displayName()).isEqualTo("Quoc Bry");
    }

    @Test
    void getCurrentUserRejectsMissingUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(1L))
                .isInstanceOf(AuthException.class)
                .hasMessage("Current user is not available");
    }

    @Test
    void getCurrentUserRejectsInactiveUser() {
        User user = activeUser(1L, "quocbry", "Quoc Bry");
        ReflectionTestUtils.setField(user, "status", "BLOCKED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getCurrentUser(1L))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void updateCurrentUserProfileUpdatesDisplayNameAndAvatar() {
        User user = activeUser(1L, "quocbry", "Old Name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateCurrentUserProfile(
                1L,
                new UpdateProfileRequest("New Name", "avatars/u-1/avatar.png")
        );

        assertThat(response.displayName()).isEqualTo("New Name");
        assertThat(response.avatarObjectKey()).isEqualTo("avatars/u-1/avatar.png");
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.now(CLOCK));

        verify(userRepository).findById(1L);
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getAvatarObjectKey()).isEqualTo("avatars/u-1/avatar.png");
        assertThat(user.getUpdatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void updateCurrentUserProfileAllowsClearingAvatar() {
        User user = activeUser(1L, "quocbry", "Quoc Bry");
        ReflectionTestUtils.setField(user, "avatarObjectKey", "avatars/old.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateCurrentUserProfile(
                1L,
                new UpdateProfileRequest("Quoc Bry", "")
        );

        assertThat(response.avatarObjectKey()).isNull();
        assertThat(user.getAvatarObjectKey()).isNull();
    }

    @Test
    void updateCurrentUserProfileRejectsMissingUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateCurrentUserProfile(
                1L,
                new UpdateProfileRequest("Quoc Bry", null)
        ))
                .isInstanceOf(AuthException.class)
                .hasMessage("Current user is not available");
    }

    @Test
    void updateCurrentUserProfileRejectsInactiveUser() {
        User user = activeUser(1L, "quocbry", "Quoc Bry");
        ReflectionTestUtils.setField(user, "status", "DELETED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateCurrentUserProfile(
                1L,
                new UpdateProfileRequest("Quoc Bry", null)
        ))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void userResponseNeverExposesPasswordHash() {
        User user = activeUser(1L, "quocbry", "Quoc Bry");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser(1L);

        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("passwordHash", "password_hash");
    }

    private User activeUser(Long id, String username, String displayName) {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        User user = User.register(
                username,
                username + "@example.com",
                "hashed-password",
                displayName,
                now
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
