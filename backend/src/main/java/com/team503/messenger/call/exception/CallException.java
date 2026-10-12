package com.team503.messenger.call.exception;

import org.springframework.http.HttpStatus;

public class CallException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CallException(HttpStatus status, String code, String message) {
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
