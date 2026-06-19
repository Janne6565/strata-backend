package com.janne6565.stratabackend.services.core;
import com.janne6565.stratabackend.configuration.kubernetes.KubernetesProperties.NamespaceScope;

import com.janne6565.stratabackend.configuration.kubernetes.KubernetesProperties;
import com.janne6565.stratabackend.entity.AccessGrantEntity;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.core.ScopeType;
import com.janne6565.stratabackend.repository.AccessGrantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrantEvaluatorTest {

    @Mock private AccessGrantRepository grantRepository;

    private GrantEvaluator evaluator(boolean safeMode) {
        return new GrantEvaluator(
                grantRepository,
                new KubernetesProperties(NamespaceScope.ALL, List.of("*prod*"), safeMode));
    }

    private DatasourceEntity datasource(String namespace) {
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(UUID.randomUUID());
        ds.setNamespace(namespace);
        return ds;
    }

    private UserEntity user(Role role) {
        return new UserEntity("u-" + role, "hash", role);
    }

    @Test
    void adminReadsAndWritesAnything() {
        GrantEvaluator evaluator = evaluator(false);
        UserEntity admin = user(Role.ADMIN);
        DatasourceEntity ds = datasource("default");

        assertThat(evaluator.canRead(admin, ds)).isTrue();
        assertThat(evaluator.canWrite(admin, ds)).isTrue();
    }

    @Test
    void namespaceGrantAllowsReadAndWrite() {
        GrantEvaluator evaluator = evaluator(false);
        UserEntity user = user(Role.USER);
        DatasourceEntity ds = datasource("default");
        when(grantRepository.findByUserId(user.getId()))
                .thenReturn(List.of(new AccessGrantEntity(user, ScopeType.NAMESPACE, "default", null, false, null)));

        assertThat(evaluator.canRead(user, ds)).isTrue();
        assertThat(evaluator.canWrite(user, ds)).isTrue();
    }

    @Test
    void readOnlyDatabaseGrantAllowsReadButNotWrite() {
        GrantEvaluator evaluator = evaluator(false);
        UserEntity user = user(Role.USER);
        DatasourceEntity ds = datasource("default");
        when(grantRepository.findByUserId(user.getId()))
                .thenReturn(List.of(new AccessGrantEntity(user, ScopeType.DATABASE, null, ds, true, null)));

        assertThat(evaluator.canRead(user, ds)).isTrue();
        assertThat(evaluator.canWrite(user, ds)).isFalse();
    }

    @Test
    void noGrantMeansNoAccess() {
        GrantEvaluator evaluator = evaluator(false);
        UserEntity user = user(Role.USER);
        DatasourceEntity ds = datasource("default");
        when(grantRepository.findByUserId(user.getId())).thenReturn(List.of());

        assertThat(evaluator.canRead(user, ds)).isFalse();
        assertThat(evaluator.canWrite(user, ds)).isFalse();
    }

    @Test
    void prodSafeModeBlocksWritesEvenForAdmin() {
        GrantEvaluator evaluator = evaluator(true);
        UserEntity admin = user(Role.OWNER);
        DatasourceEntity prod = datasource("team-prod");

        assertThat(evaluator.canRead(admin, prod)).isTrue();
        assertThat(evaluator.canWrite(admin, prod)).isFalse();
    }
}
