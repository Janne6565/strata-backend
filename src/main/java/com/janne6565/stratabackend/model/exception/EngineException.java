package com.janne6565.stratabackend.model.exception;

import com.janne6565.stratabackend.controller.ExceptionController;

/**
 * 400 — a target-database operation failed (connection, introspection, browse or query). Engine
 * adapters wrap their engine-specific failures (JDBC {@code SQLException}, driver errors) in this
 * type so the service layer and {@link ExceptionController} stay engine-agnostic. The message is
 * a safe summary — never secret material or full payloads (AUTH.md).
 */
public class EngineException extends BadRequestException {
    public EngineException(String message) {
        super(message);
    }
}
