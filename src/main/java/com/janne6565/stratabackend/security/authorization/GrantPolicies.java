package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import org.springframework.stereotype.Component;

/**
 * Authorization policies for grant management. Managing who can access which databases is an
 * administrative action, so all grant operations require {@code ADMIN} or above. Keep pure and
 * unit-testable (AUTH.md).
 */
@Component
public class GrantPolicies {

    @Validates(Operation.GRANT_LIST)
    public boolean canListGrants(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.GRANT_CREATE)
    public boolean canCreateGrant(
            ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.GRANT_REVOKE)
    public boolean canRevokeGrant(
            ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    private boolean isAdmin(UserEntity caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
