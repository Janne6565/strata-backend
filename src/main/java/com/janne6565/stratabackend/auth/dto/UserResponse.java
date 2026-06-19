package com.janne6565.stratabackend.auth.dto;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import java.util.UUID;

/** Public view of a user account (never exposes the password hash). */
public record UserResponse(UUID id, String username, Role role, boolean enabled) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isEnabled());
    }
}
