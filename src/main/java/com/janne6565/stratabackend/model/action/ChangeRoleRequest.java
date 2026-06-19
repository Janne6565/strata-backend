package com.janne6565.stratabackend.model.action;

import com.janne6565.stratabackend.model.core.Role;
import jakarta.validation.constraints.NotNull;

/** Payload for changing a user's role (admin-only). */
public record ChangeRoleRequest(@NotNull Role role) {}
