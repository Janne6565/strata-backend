package com.janne6565.stratabackend.model.exception;

import org.springframework.http.HttpStatus;

/**
 * 502 — an upstream dependency (e.g. the OAuth provider) failed or returned an unusable response.
 */
public class BadGatewayException extends BaseException {
    public BadGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
