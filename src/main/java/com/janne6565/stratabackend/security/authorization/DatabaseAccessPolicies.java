package com.janne6565.stratabackend.security.authorization;
import lombok.RequiredArgsConstructor;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.services.core.GrantEvaluator;
import org.springframework.stereotype.Component;

/**
 * Grant-gated policies for accessing a specific datasource (layer 1 of the three enforcement
 * layers, AUTH.md §6). View/browse/read need read access; write queries need write access, which
 * also honours read-only grants and global prod safe-mode via {@link GrantEvaluator}.
 */
@Component
@RequiredArgsConstructor
public class DatabaseAccessPolicies {

    private final GrantEvaluator grantEvaluator;


    @Validates(Operation.DB_VIEW)
    public boolean canView(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return canRead(resolver, referenceId, caller);
    }

    @Validates(Operation.DB_BROWSE)
    public boolean canBrowse(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return canRead(resolver, referenceId, caller);
    }

    @Validates(Operation.DB_QUERY_READ)
    public boolean canReadQuery(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        return canRead(resolver, referenceId, caller);
    }

    @Validates(Operation.DB_QUERY_WRITE)
    public boolean canWriteQuery(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        DatasourceEntity datasource = resolver.requireDatasource(referenceId);
        return grantEvaluator.canWrite(caller, datasource);
    }

    private boolean canRead(ResourceResolver resolver, Object referenceId, UserEntity caller) {
        DatasourceEntity datasource = resolver.requireDatasource(referenceId);
        return grantEvaluator.canRead(caller, datasource);
    }
}
