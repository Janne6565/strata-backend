package com.janne6565.stratabackend.common;

import org.springframework.http.HttpStatus;

/**
 * Base type for all application exceptions. Carries the HTTP status to surface; the
 * {@link GlobalExceptionHandler} renders it as an RFC 7807 {@code ProblemDetail}. Subclasses
 * keep call sites intent-revealing ({@code throw new ForbiddenException(...)}).
 */
public abstract class BaseException extends RuntimeException {

    private final HttpStatus status;

    protected BaseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
