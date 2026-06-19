package com.janne6565.stratabackend.rbac;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import org.springframework.stereotype.Component;

/**
 * Authorization policies for user-management operations. Managing users requires {@code ADMIN} or
 * above; instance-level invariants (not deleting yourself, not removing the last owner) are
 * enforced in the service layer where the full picture is available. Policies stay pure and
 * unit-testable (AUTH.md).
 */
@Component
public class UserPolicies {

    @Validates(Operation.USER_LIST)
    public boolean canListUsers(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.USER_CREATE)
    public boolean canCreateUser(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.USER_CHANGE_ROLE)
    public boolean canChangeRole(ResourceResolver resolver, Object referenceId, User caller) {
        // Touch the target so a non-existent user surfaces as 404 before the role change runs.
        resolver.requireUser(referenceId);
        return isAdmin(caller);
    }

    @Validates(Operation.USER_DELETE)
    public boolean canDeleteUser(ResourceResolver resolver, Object referenceId, User caller) {
        resolver.requireUser(referenceId);
        return isAdmin(caller);
    }

    private boolean isAdmin(User caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
