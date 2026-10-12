package com.team503.messenger.auth.security;

public class JwtValidationException extends RuntimeException {

    public JwtValidationException(String message) {
        super(message);
    }
}
