package com.janne6565.stratabackend.model.exception;

import org.springframework.http.HttpStatus;

/** 403 — the caller is authenticated but not permitted to perform the operation. */
public class ForbiddenException extends BaseException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
