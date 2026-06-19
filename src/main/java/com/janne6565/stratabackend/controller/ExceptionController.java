package com.janne6565.stratabackend.controller;

import com.janne6565.stratabackend.model.exception.BaseException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into RFC 7807 {@link ProblemDetail} responses. Never leaks stack traces or
 * credential material to clients (AUTH.md, "Secrets & sensitive data").
 */
@Slf4j
@RestControllerAdvice
public class ExceptionController {

    @ExceptionHandler(BaseException.class)
    public ProblemDetail handleBase(BaseException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(this::formatFieldError)
                        .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Validation failed" : detail);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Log the cause server-side; return a generic message so internals never reach the client.
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
