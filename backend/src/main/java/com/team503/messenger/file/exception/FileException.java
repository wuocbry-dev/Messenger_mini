package com.team503.messenger.file.exception;

import org.springframework.http.HttpStatus;

public class FileException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public FileException(HttpStatus status, String code, String message) {
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
