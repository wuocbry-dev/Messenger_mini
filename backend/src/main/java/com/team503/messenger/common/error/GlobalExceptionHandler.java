package com.team503.messenger.common.error;

import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.call.exception.CallException;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.file.exception.FileException;
import com.team503.messenger.message.exception.MessageException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ErrorResponse> handleAuthException(AuthException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorResponse(
                Instant.now(),
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(ConversationException.class)
    ResponseEntity<ErrorResponse> handleConversationException(ConversationException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorResponse(
                Instant.now(),
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MessageException.class)
    ResponseEntity<ErrorResponse> handleMessageException(MessageException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorResponse(
                Instant.now(),
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(FileException.class)
    ResponseEntity<ErrorResponse> handleFileException(FileException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorResponse(
                Instant.now(),
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(CallException.class)
    ResponseEntity<ErrorResponse> handleCallException(CallException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorResponse(
                Instant.now(),
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse("Request validation failed");

        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                message,
                request.getRequestURI()
        ));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
