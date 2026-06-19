package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
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
    public boolean canListUsers(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.USER_CREATE)
    public boolean canCreateUser(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.USER_CHANGE_ROLE)
    public boolean canChangeRole(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        // Touch the target so a non-existent user surfaces as 404 before the role change runs.
        resolver.requireUser(referenceId);
        return isAdmin(caller);
    }

    @Validates(Operation.USER_DELETE)
    public boolean canDeleteUser(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        resolver.requireUser(referenceId);
        return isAdmin(caller);
    }

    private boolean isAdmin(UserEntity caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
