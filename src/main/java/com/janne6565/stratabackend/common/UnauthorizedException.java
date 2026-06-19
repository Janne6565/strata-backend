package com.janne6565.stratabackend.common;

import org.springframework.http.HttpStatus;

/** 401 — the request is unauthenticated (missing/invalid token, bad credentials). */
public class UnauthorizedException extends BaseException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
