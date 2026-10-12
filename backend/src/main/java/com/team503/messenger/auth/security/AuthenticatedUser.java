package com.team503.messenger.auth.security;

import java.security.Principal;

/**
 * Implements {@link Principal} so {@code Authentication.getName()} returns the
 * username. STOMP user destinations ({@code convertAndSendToUser}) look up
 * sessions by that name; without it Spring falls back to {@code toString()}
 * and per-user realtime events are never delivered.
 */
public record AuthenticatedUser(
        Long id,
        String username
) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
