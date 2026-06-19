package com.janne6565.stratabackend.rbac;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import org.springframework.stereotype.Component;

/**
 * Authorization policies for grant management. Managing who can access which databases is an
 * administrative action, so all grant operations require {@code ADMIN} or above. Keep pure and
 * unit-testable (AUTH.md).
 */
@Component
public class GrantPolicies {

    @Validates(Operation.GRANT_LIST)
    public boolean canListGrants(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.GRANT_CREATE)
    public boolean canCreateGrant(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.GRANT_REVOKE)
    public boolean canRevokeGrant(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    private boolean isAdmin(User caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
