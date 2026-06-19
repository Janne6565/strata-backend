package com.janne6565.stratabackend.services.core;

import com.janne6565.stratabackend.configuration.kubernetes.KubernetesProperties;
import com.janne6565.stratabackend.entity.AccessGrantEntity;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.repository.AccessGrantRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Central access decision over grants + global prod safe-mode (AUTH.md "Roles, grants, scoping").
 * A grant covers a datasource if it targets the datasource directly (DATABASE scope) or its
 * namespace (NAMESPACE scope). Admins see everything. Writes additionally require a non-read-only
 * grant and that prod safe-mode does not apply — the most restrictive rule wins.
 */
@Component
public class GrantEvaluator {

    private final AccessGrantRepository grantRepository;
    private final KubernetesProperties kubernetesProperties;

    public GrantEvaluator(
            AccessGrantRepository grantRepository, KubernetesProperties kubernetesProperties) {
        this.grantRepository = grantRepository;
        this.kubernetesProperties = kubernetesProperties;
    }

    /** Whether the caller may view/browse/read the datasource. */
    public boolean canRead(UserEntity caller, DatasourceEntity datasource) {
        return isAdmin(caller) || hasCoveringGrant(caller, datasource, false);
    }

    /** Whether the caller may issue writes — needs a writable grant and no prod safe-mode block. */
    public boolean canWrite(UserEntity caller, DatasourceEntity datasource) {
        if (prodSafeModeBlocks(datasource.getNamespace())) {
            return false;
        }
        return isAdmin(caller) || hasCoveringGrant(caller, datasource, true);
    }

    private boolean hasCoveringGrant(UserEntity caller, DatasourceEntity datasource, boolean requireWritable) {
        List<AccessGrantEntity> grants = grantRepository.findByUserId(caller.getId());
        for (AccessGrantEntity grant : grants) {
            if (covers(grant, datasource) && (!requireWritable || !grant.isReadOnly())) {
                return true;
            }
        }
        return false;
    }

    private boolean covers(AccessGrantEntity grant, DatasourceEntity datasource) {
        return switch (grant.getScopeType()) {
            case NAMESPACE -> datasource.getNamespace().equals(grant.getNamespace());
            // getId() on a lazy proxy does not hit the DB, so this is safe outside a session.
            case DATABASE ->
                    grant.getDatasource() != null
                            && datasource.getId().equals(grant.getDatasource().getId());
        };
    }

    private boolean prodSafeModeBlocks(String namespace) {
        if (!kubernetesProperties.readOnlySafeMode()
                || kubernetesProperties.prodNamespacePatterns() == null) {
            return false;
        }
        return kubernetesProperties.prodNamespacePatterns().stream()
                .anyMatch(pattern -> namespace.matches(globToRegex(pattern)));
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return regex.toString();
    }

    private boolean isAdmin(UserEntity caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
