package com.janne6565.stratabackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Credentials submitted to {@code POST /api/auth/login}. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
