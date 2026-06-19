package com.janne6565.stratabackend.auth.dto;

import com.janne6565.stratabackend.auth.Role;
import jakarta.validation.constraints.NotNull;

/** Payload for changing a user's role (admin-only). */
public record ChangeRoleRequest(@NotNull Role role) {}
