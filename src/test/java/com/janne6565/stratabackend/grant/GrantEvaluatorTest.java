package com.janne6565.stratabackend.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.config.properties.KubernetesProperties;
import com.janne6565.stratabackend.config.properties.KubernetesProperties.NamespaceScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantEvaluatorTest {

    @Mock private AccessGrantRepository grantRepository;

    private GrantEvaluator evaluator(boolean safeMode) {
        return new GrantEvaluator(
                grantRepository,
                new KubernetesProperties(NamespaceScope.ALL, List.of("*prod*"), safeMode));
    }

    private Datasource datasource(String namespace) {
        Datasource ds = new Datasource();
        ds.setId(UUID.randomUUID());
        ds.setNamespace(namespace);
        return ds;
    }

    private User user(Role role) {
        return new User("u-" + role, "hash", role);
    }

    @Test
    void adminReadsAndWritesAnything() {
        GrantEvaluator evaluator = evaluator(false);
        User admin = user(Role.ADMIN);
        Datasource ds = datasource("default");

        assertThat(evaluator.canRead(admin, ds)).isTrue();
        assertThat(evaluator.canWrite(admin, ds)).isTrue();
    }

    @Test
    void namespaceGrantAllowsReadAndWrite() {
        GrantEvaluator evaluator = evaluator(false);
        User user = user(Role.USER);
        Datasource ds = datasource("default");
        when(grantRepository.findByUserId(user.getId()))
                .thenReturn(List.of(new AccessGrant(user, ScopeType.NAMESPACE, "default", null, false, null)));

        assertThat(evaluator.canRead(user, ds)).isTrue();
        assertThat(evaluator.canWrite(user, ds)).isTrue();
    }

    @Test
    void readOnlyDatabaseGrantAllowsReadButNotWrite() {
        GrantEvaluator evaluator = evaluator(false);
        User user = user(Role.USER);
        Datasource ds = datasource("default");
        when(grantRepository.findByUserId(user.getId()))
                .thenReturn(List.of(new AccessGrant(user, ScopeType.DATABASE, null, ds, true, null)));

        assertThat(evaluator.canRead(user, ds)).isTrue();
        assertThat(evaluator.canWrite(user, ds)).isFalse();
    }

    @Test
    void noGrantMeansNoAccess() {
        GrantEvaluator evaluator = evaluator(false);
        User user = user(Role.USER);
        Datasource ds = datasource("default");
        when(grantRepository.findByUserId(user.getId())).thenReturn(List.of());

        assertThat(evaluator.canRead(user, ds)).isFalse();
        assertThat(evaluator.canWrite(user, ds)).isFalse();
    }

    @Test
    void prodSafeModeBlocksWritesEvenForAdmin() {
        GrantEvaluator evaluator = evaluator(true);
        User admin = user(Role.OWNER);
        Datasource prod = datasource("team-prod");

        assertThat(evaluator.canRead(admin, prod)).isTrue();
        assertThat(evaluator.canWrite(admin, prod)).isFalse();
    }
}
