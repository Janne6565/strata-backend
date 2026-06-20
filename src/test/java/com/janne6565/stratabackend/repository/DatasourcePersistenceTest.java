package com.janne6565.stratabackend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.janne6565.stratabackend.TestcontainersConfiguration;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.CredentialResolution;
import com.janne6565.stratabackend.model.core.CredentialSource;
import com.janne6565.stratabackend.model.core.DatasourceOrigin;
import com.janne6565.stratabackend.model.core.DatasourceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Verifies the jsonb columns round-trip through Hibernate + the configured JSON mapper. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatasourcePersistenceTest {

    @Autowired private DatasourceRepository repository;

    @Test
    void roundTripsJsonbColumns() {
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(UUID.randomUUID());
        ds.setDiscoveryKey("ns/Deployment/pg-" + ds.getId());
        ds.setOrigin(DatasourceOrigin.DISCOVERED);
        ds.setStatus(DatasourceStatus.PRESENT);
        ds.setNamespace("ns");
        ds.setDriver("postgresql");
        ds.setDisplayName("orders-db");
        Instant now = Instant.now();
        ds.setFirstSeenAt(now);
        ds.setLastSeenAt(now);
        ds.setCreatedAt(now);
        ds.setCredentialResolution(
                new CredentialResolution(
                        "pg",
                        List.of(
                                CredentialSource.secret("password", "pg-secret", "password"),
                                CredentialSource.literal("username", "POSTGRES_USER"))));
        ds.setManualOverrides(Map.of("displayName", "Custom Name"));

        repository.saveAndFlush(ds);

        DatasourceEntity loaded = repository.findById(ds.getId()).orElseThrow();
        assertThat(loaded.getCredentialResolution().strategy()).isEqualTo("pg");
        assertThat(loaded.getCredentialResolution().sources()).hasSize(2);
        assertThat(loaded.getCredentialResolution().sources().get(0).name()).isEqualTo("pg-secret");
        assertThat(loaded.getManualOverrides()).containsEntry("displayName", "Custom Name");
    }
}
