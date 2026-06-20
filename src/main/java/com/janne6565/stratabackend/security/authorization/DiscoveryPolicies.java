package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import org.springframework.stereotype.Component;

/**
 * Authorization policies for discovery and the datasource catalog. Discovery, registration and (for
 * now) inventory viewing are administrative actions, so all require {@code ADMIN} or above. M3 will
 * refine {@code DB_VIEW} into grant-scoped access for regular users.
 */
@Component
public class DiscoveryPolicies {

    @Validates(Operation.DISCOVERY_RESCAN)
    public boolean canRescan(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.DB_REGISTER)
    public boolean canRegister(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.DB_UNREGISTER)
    public boolean canUnregister(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.DB_RENAME)
    public boolean canRename(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        // The display name is shared catalog metadata, so editing it is administrative.
        return isAdmin(caller);
    }

    private boolean isAdmin(UserEntity caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
