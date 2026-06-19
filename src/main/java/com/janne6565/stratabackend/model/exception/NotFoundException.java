package com.janne6565.stratabackend.model.exception;

import org.springframework.http.HttpStatus;

/** 404 — the referenced resource does not exist. */
public class NotFoundException extends BaseException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
