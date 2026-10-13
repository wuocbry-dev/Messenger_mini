package com.team503.messenger.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team503.messenger.file.entity.StoredFile;
import com.team503.messenger.file.exception.FileException;
import com.team503.messenger.file.repository.StoredFileRepository;
import com.team503.messenger.file.service.ObjectStorageService;
import com.team503.messenger.presence.service.PresenceService;
import com.team503.messenger.user.dto.UpdateProfileRequest;
import com.team503.messenger.user.dto.UserResponse;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceAvatarTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private static final long USER_ID = 7L;
    private static final String KEY = "messenger/u7/abc.png";

    private UserRepository userRepository;
    private StoredFileRepository fileRepository;
    private ObjectStorageService storage;
    private User user;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        fileRepository = mock(StoredFileRepository.class);
        storage = mock(ObjectStorageService.class);
        user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getId()).thenReturn(USER_ID);
        when(user.getUsername()).thenReturn("alice");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(storage.createDownloadUrl(KEY)).thenReturn(URI.create("https://r2.example/avatar?sig=1"));
        service = new UserService(userRepository, fileRepository, new AvatarUrlResolver(storage), mock(PresenceService.class), CLOCK);
    }

    private StoredFile file(long uploaderId, String mime) {
        StoredFile f = mock(StoredFile.class);
        when(f.getUploaderId()).thenReturn(uploaderId);
        when(f.getMimeType()).thenReturn(mime);
        return f;
    }

    @Test
    void acceptsOwnImageAndReturnsPresignedUrl() {
        StoredFile stored = file(USER_ID, "image/png");
        when(fileRepository.findByObjectKey(KEY)).thenReturn(Optional.of(stored));
        when(user.getAvatarObjectKey()).thenReturn(null, KEY);

        UserResponse res = service.updateCurrentUserProfile(USER_ID, new UpdateProfileRequest("Alice", KEY));

        assertThat(res.avatarUrl()).isEqualTo("https://r2.example/avatar?sig=1");
    }

    @Test
    void rejectsAnotherUsersFile() {
        StoredFile stored = file(99L, "image/png");
        when(fileRepository.findByObjectKey(KEY)).thenReturn(Optional.of(stored));
        assertThatThrownBy(() -> service.updateCurrentUserProfile(USER_ID, new UpdateProfileRequest("Alice", KEY)))
                .isInstanceOf(FileException.class)
                .extracting("code").isEqualTo("INVALID_AVATAR");
    }

    @Test
    void rejectsNonImageFile() {
        StoredFile stored = file(USER_ID, "application/pdf");
        when(fileRepository.findByObjectKey(KEY)).thenReturn(Optional.of(stored));
        assertThatThrownBy(() -> service.updateCurrentUserProfile(USER_ID, new UpdateProfileRequest("Alice", KEY)))
                .isInstanceOf(FileException.class);
    }

    @Test
    void rejectsUnknownObjectKey() {
        when(fileRepository.findByObjectKey(KEY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateCurrentUserProfile(USER_ID, new UpdateProfileRequest("Alice", KEY)))
                .isInstanceOf(FileException.class);
    }

    @Test
    void keepingTheCurrentAvatarSkipsRevalidation() {
        when(user.getAvatarObjectKey()).thenReturn(KEY);
        service.updateCurrentUserProfile(USER_ID, new UpdateProfileRequest("Alice", KEY));
        // no stub for findByObjectKey: an unexpected lookup would return null and fail
    }

    @Test
    void blankAvatarClearsItAndYieldsNoUrl() {
        when(user.getAvatarObjectKey()).thenReturn(null);
        UserResponse res = service.updateCurrentUserProfile(USER_ID, new UpdateProfileRequest("Alice", " "));
        assertThat(res.avatarUrl()).isNull();
    }

    @Test
    void storageFailureDegradesToNoUrl() {
        when(user.getAvatarObjectKey()).thenReturn(KEY);
        when(storage.createDownloadUrl(KEY)).thenThrow(new IllegalStateException("R2 down"));
        assertThat(service.getCurrentUser(USER_ID).avatarUrl()).isNull();
    }
}
