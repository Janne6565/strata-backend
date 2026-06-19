package com.janne6565.stratabackend.model.action;

import com.janne6565.stratabackend.model.core.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for creating a local user account (admin-only). */
public record CreateUserRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(min = 8, max = 255) String password,
        @NotNull Role role) {}
