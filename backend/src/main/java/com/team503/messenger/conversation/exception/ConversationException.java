package com.team503.messenger.conversation.exception;

import org.springframework.http.HttpStatus;

public class ConversationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ConversationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
