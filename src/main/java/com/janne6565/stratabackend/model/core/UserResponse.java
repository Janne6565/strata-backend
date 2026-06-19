package com.janne6565.stratabackend.model.core;

import com.janne6565.stratabackend.entity.UserEntity;
import java.util.UUID;

/** Public view of a user account (never exposes the password hash). */
public record UserResponse(UUID id, String username, Role role, boolean enabled) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isEnabled());
    }
}
