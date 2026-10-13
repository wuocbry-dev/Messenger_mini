package com.team503.messenger.presence.dto;

import com.team503.messenger.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Realtime presence snapshot published to {@code /user/{username}/queue/presence}.
 *
 * @param userId     the user whose presence changed
 * @param online     true if the user has at least one live WebSocket session
 * @param lastSeenAt server-known last activity timestamp; null if the user
 *                   has never been seen
 */
@Schema(description = "Presence snapshot pushed over STOMP to /user/{username}/queue/presence.")
public record PresenceUpdate(

        @Schema(description = "User id whose presence changed.", example = "42")
        Long userId,

        @Schema(description = "True if the user has at least one live WebSocket session.", example = "true")
        boolean online,

        @Schema(description = "Server-known last activity timestamp (UTC). Null if the user has never been seen.", example = "2026-10-13T11:24:00", nullable = true)
        LocalDateTime lastSeenAt
) {

    public static PresenceUpdate online(Long userId, LocalDateTime now) {
        return new PresenceUpdate(userId, true, now);
    }

    public static PresenceUpdate offline(Long userId, LocalDateTime lastSeenAt) {
        return new PresenceUpdate(userId, false, lastSeenAt);
    }

    public static PresenceUpdate fromUser(User user, boolean online) {
        return new PresenceUpdate(user.getId(), online, user.getLastSeenAt());
    }
}
