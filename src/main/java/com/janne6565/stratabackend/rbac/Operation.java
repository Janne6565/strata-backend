package com.janne6565.stratabackend.rbac;

/**
 * The exhaustive catalogue of authorization-protected operations (AUTH.md, ARCHITECTURE.md §6).
 * Every constant must have exactly one {@code @Validates} policy — {@link ValidatorRegistry}
 * fails fast at startup otherwise. M1 covers the user-management operations; later milestones add
 * {@code GRANT_*}, {@code DISCOVERY_*}, {@code DB_*}.
 */
public enum Operation {
    USER_LIST,
    USER_CREATE,
    USER_CHANGE_ROLE,
    USER_DELETE,
    GRANT_LIST,
    GRANT_CREATE,
    GRANT_REVOKE,
    DISCOVERY_RESCAN,
    DB_REGISTER,
    DB_UNREGISTER,
    DB_VIEW,
    DB_BROWSE,
    DB_QUERY_READ,
    DB_QUERY_WRITE
}
