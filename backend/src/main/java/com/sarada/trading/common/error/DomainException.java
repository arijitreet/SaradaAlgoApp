package com.sarada.trading.common.error;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final HttpStatus status;

    public DomainException(String message) {
        this(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public DomainException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static DomainException notFound(String message) {
        return new DomainException(message, HttpStatus.NOT_FOUND);
    }

    public static DomainException conflict(String message) {
        return new DomainException(message, HttpStatus.CONFLICT);
    }
}
