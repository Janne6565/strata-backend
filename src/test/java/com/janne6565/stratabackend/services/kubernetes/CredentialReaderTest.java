package com.janne6565.stratabackend.services.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.CredentialResolution;
import com.janne6565.stratabackend.model.core.CredentialSource;
import com.janne6565.stratabackend.model.core.CredentialSourceType;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CredentialReaderTest {

    private final KubernetesClient client = mock(KubernetesClient.class);
    private final KubernetesScanner scanner = mock(KubernetesScanner.class);
    private final CredentialReader reader = new CredentialReader(client, scanner);

    private DatasourceEntity datasource(CredentialSource... sources) {
        DatasourceEntity ds = new DatasourceEntity();
        ds.setNamespace("cosy");
        ds.setWorkloadKind("StatefulSet");
        ds.setWorkloadName("cosy-postgres");
        ds.setServiceName("cosy-postgres");
        ds.setServicePort(5432);
        ds.setDriver("postgresql");
        ds.setCredentialResolution(new CredentialResolution("postgres", List.of(sources)));
        return ds;
    }

    private Container containerWithEnv(String name, String value) {
        return new ContainerBuilder()
                .withName("postgres")
                .withEnv(new EnvVarBuilder().withName(name).withValue(value).build())
                .build();
    }

    @Test
    void resolvesInlineLiteralByReadingTheLiveWorkloadEnv() {
        DatasourceEntity ds = datasource(CredentialSource.literal("database", "POSTGRES_DB"));
        when(scanner.primaryContainer("cosy", "StatefulSet", "cosy-postgres"))
                .thenReturn(Optional.of(containerWithEnv("POSTGRES_DB", "cosydb")));

        ConnectionDetails details = reader.resolve(ds);

        assertThat(details.database()).isEqualTo("cosydb");
        assertThat(details.host()).isEqualTo("cosy-postgres.cosy.svc.cluster.local");
        assertThat(details.port()).isEqualTo(5432);
    }

    @Test
    void requiresManualOverrideWhenTheLiteralEnvVarIsGone() {
        DatasourceEntity ds = datasource(CredentialSource.literal("database", "POSTGRES_DB"));
        // The env var no longer carries an inline value (e.g. moved to a secretKeyRef).
        when(scanner.primaryContainer("cosy", "StatefulSet", "cosy-postgres"))
                .thenReturn(Optional.of(containerWithEnv("SOMETHING_ELSE", "x")));

        assertThatThrownBy(() -> reader.resolve(ds))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("database")
                .hasMessageContaining("manual override");
    }

    @Test
    void requiresManualOverrideForLegacyLiteralWithoutEnvVarName() {
        // A resolution persisted before LITERAL sources recorded the env var name.
        DatasourceEntity ds =
                datasource(
                        new CredentialSource("database", CredentialSourceType.LITERAL, null, null));

        assertThatThrownBy(() -> reader.resolve(ds))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("manual override");
    }
}
