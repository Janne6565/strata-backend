package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.DbGroupEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import org.springframework.stereotype.Component;

/**
 * Authorization policies for group management (AUTH.md, ARCHITECTURE.md §6). Groups are private to
 * their owner: listing and creating are available to any authenticated user (scoped to the caller
 * in the service), while modifying or deleting a specific group requires ownership (or admin).
 * Collection-level update operations (reorder) carry no {@code referenceId} and are allowed,
 * with the service scoping the change to the caller's groups. Keep pure and unit-testable.
 */
@Component
public class GroupPolicies {

    @Validates(Operation.GROUP_LIST)
    public boolean canListGroups(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return true;
    }

    @Validates(Operation.GROUP_CREATE)
    public boolean canCreateGroup(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return true;
    }

    @Validates(Operation.GROUP_UPDATE)
    public boolean canUpdateGroup(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        if (referenceId == null) {
            return true; // collection-level (reorder): service scopes to the caller's own groups
        }
        return ownsOrAdmin(resolver.requireGroup(referenceId), caller);
    }

    @Validates(Operation.GROUP_DELETE)
    public boolean canDeleteGroup(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return ownsOrAdmin(resolver.requireGroup(referenceId), caller);
    }

    private boolean ownsOrAdmin(DbGroupEntity group, UserEntity caller) {
        return group.getOwnerUserId().equals(caller.getId())
                || caller.getRole().isAtLeast(Role.ADMIN);
    }
}
