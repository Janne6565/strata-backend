package com.janne6565.stratabackend.common;

import org.springframework.http.HttpStatus;

/** 409 — the request conflicts with current state (e.g. a duplicate unique value). */
public class ConflictException extends BaseException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
