package com.janne6565.stratabackend.auth.dto;

import java.time.Instant;

/** A successful login: the bearer token, its expiry, and the authenticated user. */
public record LoginResponse(String token, Instant expiresAt, UserResponse user) {}
