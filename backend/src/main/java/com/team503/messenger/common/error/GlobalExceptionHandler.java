package com.team503.messenger.common.error;

import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.call.exception.CallException;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.file.exception.FileException;
import com.team503.messenger.message.exception.MessageException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof HttpMessageNotReadableException
                ? "Malformed request body"
                : exception.getMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                message,
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request)
            throws Exception {
        // Let Spring MVC resolve its own exceptions (404 no handler, 405, 415, ...).
        if (exception instanceof org.springframework.web.ErrorResponse) {
            throw exception;
        }
        log.error("Unhandled exception on {}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Unexpected server error",
                request.getRequestURI()
        ));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
