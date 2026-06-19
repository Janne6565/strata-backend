package com.janne6565.stratabackend.rbac;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import org.springframework.stereotype.Component;

/**
 * Authorization policies for discovery and the datasource catalog. Discovery, registration and
 * (for now) inventory viewing are administrative actions, so all require {@code ADMIN} or above.
 * M3 will refine {@code DB_VIEW} into grant-scoped access for regular users.
 */
@Component
public class DiscoveryPolicies {

    @Validates(Operation.DISCOVERY_RESCAN)
    public boolean canRescan(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.DB_REGISTER)
    public boolean canRegister(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.DB_UNREGISTER)
    public boolean canUnregister(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    @Validates(Operation.DB_VIEW)
    public boolean canView(ResourceResolver resolver, Object referenceId, User caller) {
        return isAdmin(caller);
    }

    private boolean isAdmin(User caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
