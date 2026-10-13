package com.team503.messenger.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team503.messenger.file.repository.StoredFileRepository;
import com.team503.messenger.file.service.ObjectStorageService;
import com.team503.messenger.presence.service.PresenceService;
import com.team503.messenger.user.dto.UserSearchResult;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserServiceDirectoryTest {

    @Test
    void directoryEntriesCarryPresenceAndLastSeen() {
        UserRepository users = mock(UserRepository.class);
        PresenceService presence = mock(PresenceService.class);
        LocalDateTime seen = LocalDateTime.of(2026, 10, 13, 9, 0);

        User online = user(2L, "bob", seen);
        User offline = user(3L, "carol", null);
        when(users.findAllActiveUsersExcept(1L)).thenReturn(List.of(online, offline));
        when(presence.isOnline(2L)).thenReturn(true);
        when(presence.isOnline(3L)).thenReturn(false);

        UserService service = new UserService(
                users,
                mock(StoredFileRepository.class),
                new AvatarUrlResolver(mock(ObjectStorageService.class)),
                presence,
                Clock.systemUTC());

        List<UserSearchResult> result = service.getAllActiveUsers(1L);

        assertThat(result).extracting(UserSearchResult::online).containsExactly(true, false);
        assertThat(result.get(0).lastSeenAt()).isEqualTo(seen);
        assertThat(result.get(1).lastSeenAt()).isNull();
    }

    private static User user(long id, String username, LocalDateTime lastSeen) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.getUsername()).thenReturn(username);
        when(u.getDisplayName()).thenReturn(username);
        when(u.getLastSeenAt()).thenReturn(lastSeen);
        return u;
    }
}
