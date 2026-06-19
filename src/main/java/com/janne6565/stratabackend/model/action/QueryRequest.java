package com.janne6565.stratabackend.model.action;

import jakarta.validation.constraints.NotBlank;

/** A SQL statement submitted to the query/execute endpoints. */
public record QueryRequest(@NotBlank String sql) {}
