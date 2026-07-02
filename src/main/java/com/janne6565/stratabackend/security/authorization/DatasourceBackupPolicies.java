package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.services.core.GrantEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Grant-gated policies for a datasource's backups. A backup is a full offline copy of the database,
 * so listing and downloading require read access to that datasource — the same grant that lets the
 * caller browse or read-query it (a dump exposes no more than they could already read). Mirrors
 * {@link DatabaseAccessPolicies}.
 */
@Component
@RequiredArgsConstructor
public class DatasourceBackupPolicies {

    private final GrantEvaluator grantEvaluator;

    @Validates(Operation.DATASOURCE_BACKUP_LIST)
    public boolean canListBackups(
            ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return canRead(resolver, referenceId, caller);
    }

    @Validates(Operation.DATASOURCE_BACKUP_DOWNLOAD)
    public boolean canDownloadBackup(
            ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return canRead(resolver, referenceId, caller);
    }

    private boolean canRead(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        DatasourceEntity datasource = resolver.requireDatasource(referenceId);
        return grantEvaluator.canRead(caller, datasource);
    }
}
