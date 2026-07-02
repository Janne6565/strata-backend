package com.janne6565.stratabackend.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.services.core.GrantEvaluator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasourceBackupPoliciesTest {

    @Mock private GrantEvaluator grantEvaluator;
    @Mock private ResourceResolver resolver;
    @Mock private DatasourceEntity datasource;

    private final UUID datasourceId = UUID.randomUUID();
    private final UserEntity caller = new UserEntity("alice", "hash", Role.USER);

    @Test
    void listAndDownloadAllowedWhenCallerHasReadAccess() {
        DatasourceBackupPolicies policies = new DatasourceBackupPolicies(grantEvaluator);
        when(resolver.requireDatasource(datasourceId)).thenReturn(datasource);
        when(grantEvaluator.canRead(caller, datasource)).thenReturn(true);

        assertThat(policies.canListBackups(resolver, datasourceId, caller)).isTrue();
        assertThat(policies.canDownloadBackup(resolver, datasourceId, caller)).isTrue();
        assertThat(policies.canCreateBackup(resolver, datasourceId, caller)).isTrue();
    }

    @Test
    void deniedWhenCallerLacksReadAccess() {
        DatasourceBackupPolicies policies = new DatasourceBackupPolicies(grantEvaluator);
        when(resolver.requireDatasource(datasourceId)).thenReturn(datasource);
        when(grantEvaluator.canRead(caller, datasource)).thenReturn(false);

        assertThat(policies.canListBackups(resolver, datasourceId, caller)).isFalse();
        assertThat(policies.canDownloadBackup(resolver, datasourceId, caller)).isFalse();
        assertThat(policies.canCreateBackup(resolver, datasourceId, caller)).isFalse();
    }
}
