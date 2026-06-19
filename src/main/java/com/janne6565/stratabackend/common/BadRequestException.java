package com.janne6565.stratabackend.common;

import org.springframework.http.HttpStatus;

/** 400 — the request is malformed or violates a business rule. */
public class BadRequestException extends BaseException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
