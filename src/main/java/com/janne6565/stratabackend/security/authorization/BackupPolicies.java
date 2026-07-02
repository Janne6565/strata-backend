package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import org.springframework.stereotype.Component;

/**
 * Authorization policies for database backups. Backups contain every user's password hash, so they
 * are the most sensitive artifact in the system — access is restricted to {@code OWNER}, stricter
 * than the {@code ADMIN}-gated management screens. Policies stay pure and unit-testable (AUTH.md).
 */
@Component
public class BackupPolicies {

    @Validates(Operation.BACKUP_LIST)
    public boolean canListBackups(
            ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isOwner(caller);
    }

    @Validates(Operation.BACKUP_DOWNLOAD)
    public boolean canDownloadBackup(
            ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return isOwner(caller);
    }

    private boolean isOwner(UserEntity caller) {
        return caller.getRole().isAtLeast(Role.OWNER);
    }
}
