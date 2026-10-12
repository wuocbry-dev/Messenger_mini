package com.team503.messenger.auth.security;

public record AuthenticatedUser(
        Long id,
        String username
) {
}
