package com.janne6565.stratabackend.grant;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.config.properties.KubernetesProperties;
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
    public boolean canRead(User caller, Datasource datasource) {
        return isAdmin(caller) || hasCoveringGrant(caller, datasource, false);
    }

    /** Whether the caller may issue writes — needs a writable grant and no prod safe-mode block. */
    public boolean canWrite(User caller, Datasource datasource) {
        if (prodSafeModeBlocks(datasource.getNamespace())) {
            return false;
        }
        return isAdmin(caller) || hasCoveringGrant(caller, datasource, true);
    }

    private boolean hasCoveringGrant(User caller, Datasource datasource, boolean requireWritable) {
        List<AccessGrant> grants = grantRepository.findByUserId(caller.getId());
        for (AccessGrant grant : grants) {
            if (covers(grant, datasource) && (!requireWritable || !grant.isReadOnly())) {
                return true;
            }
        }
        return false;
    }

    private boolean covers(AccessGrant grant, Datasource datasource) {
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

    private boolean isAdmin(User caller) {
        return caller.getRole().isAtLeast(Role.ADMIN);
    }
}
