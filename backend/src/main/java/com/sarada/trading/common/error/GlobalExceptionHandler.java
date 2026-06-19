package com.sarada.trading.common.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(String message, int status, Instant timestamp) {
        static ApiError of(String message, HttpStatus status) {
            return new ApiError(message, status.value(), Instant.now());
        }
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException ex) {
        return ResponseEntity.status(ex.status()).body(ApiError.of(ex.getMessage(), ex.status()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiError.of(message, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("Internal error", HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
