package com.janne6565.stratabackend.auth;

/**
 * Fixed role hierarchy (see ARCHITECTURE.md §6): {@code OWNER} ▸ {@code ADMIN} ▸ {@code USER}.
 * A higher role implicitly satisfies any lower one via {@link #isAtLeast(Role)}.
 */
public enum Role {
    USER,
    ADMIN,
    OWNER;

    /** True when this role is the given role or ranks above it. */
    public boolean isAtLeast(Role other) {
        return ordinal() >= other.ordinal();
    }
}
