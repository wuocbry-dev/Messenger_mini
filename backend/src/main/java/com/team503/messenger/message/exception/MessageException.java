package com.team503.messenger.message.exception;

import org.springframework.http.HttpStatus;

public class MessageException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MessageException(HttpStatus status, String code, String message) {
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
