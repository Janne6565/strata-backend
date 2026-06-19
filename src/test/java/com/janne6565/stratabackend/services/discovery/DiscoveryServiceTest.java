package com.janne6565.stratabackend.services.discovery;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.Detector;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.Match;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.Credentials;

import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.DatasourceOrigin;
import com.janne6565.stratabackend.model.core.DatasourceStatus;
import com.janne6565.stratabackend.model.core.DiscoverySummary;
import com.janne6565.stratabackend.model.core.WorkloadDescriptor;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.kubernetes.CredentialResolver;
import com.janne6565.stratabackend.services.kubernetes.KubernetesScanner;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock private KubernetesScanner scanner;
    @Mock private DatasourceRepository datasourceRepository;

    private DiscoveryService service;

    @BeforeEach
    void setUp() {
        DiscoveryProperties props =
                new DiscoveryProperties(
                        List.of(
                                new Detector(
                                        "pg",
                                        "postgresql",
                                        new Match("postgres", List.of(5432)),
                                        new Credentials("pg", Map.of("password", "PGPASSWORD"), null))));
        service =
                new DiscoveryService(
                        scanner,
                        new DetectorMatcher(props),
                        new CredentialResolver(),
                        datasourceRepository,
                        props);
    }

    private WorkloadDescriptor pgWorkload(String name) {
        Container container =
                new ContainerBuilder()
                        .withName("db")
                        .addNewEnv()
                        .withName("PGPASSWORD")
                        .withNewValueFrom()
                        .withNewSecretKeyRef("password", "pg-secret", false)
                        .endValueFrom()
                        .endEnv()
                        .build();
        return new WorkloadDescriptor(
                "default", "Deployment", name, "postgres:17", List.of(5432), container, name + "-svc", 5432);
    }

    private DatasourceEntity stale(String discoveryKey, DatasourceStatus status) {
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(UUID.randomUUID());
        ds.setDiscoveryKey(discoveryKey);
        ds.setOrigin(DatasourceOrigin.DISCOVERED);
        ds.setStatus(status);
        ds.setNamespace("default");
        ds.setDriver("postgresql");
        ds.setDisplayName("old");
        Instant t = Instant.now();
        ds.setFirstSeenAt(t);
        ds.setLastSeenAt(t);
        ds.setCreatedAt(t);
        return ds;
    }

    @Test
    void insertsNewlyDiscoveredWorkload() {
        when(scanner.scan()).thenReturn(List.of(pgWorkload("orders")));
        when(datasourceRepository.findByDiscoveryKey("default/Deployment/orders"))
                .thenReturn(Optional.empty());
        when(datasourceRepository.findAll()).thenReturn(List.of());

        DiscoverySummary summary = service.rescan();

        assertThat(summary.created()).isEqualTo(1);
        ArgumentCaptor<DatasourceEntity> captor = ArgumentCaptor.forClass(DatasourceEntity.class);
        verify(datasourceRepository).save(captor.capture());
        DatasourceEntity saved = captor.getValue();
        assertThat(saved.getDriver()).isEqualTo("postgresql");
        assertThat(saved.getOrigin()).isEqualTo(DatasourceOrigin.DISCOVERED);
        assertThat(saved.getStatus()).isEqualTo(DatasourceStatus.PRESENT);
        assertThat(saved.getDetectionConfidence()).isEqualTo("HIGH");
        assertThat(saved.getCredentialResolution().sources()).hasSize(1);
        assertThat(saved.getServicePort()).isEqualTo(5432);
    }

    @Test
    void skipsWorkloadsThatMatchNoDetector() {
        Container redis = new ContainerBuilder().withName("cache").build();
        when(scanner.scan())
                .thenReturn(
                        List.of(
                                new WorkloadDescriptor(
                                        "default", "Deployment", "cache", "redis:7", List.of(6379), redis, null, null)));
        when(datasourceRepository.findAll()).thenReturn(List.of());

        DiscoverySummary summary = service.rescan();

        assertThat(summary.matched()).isZero();
        verify(datasourceRepository, never()).save(any());
    }

    @Test
    void marksRowsNotSeenThisScanAsMissing() {
        DatasourceEntity gone = stale("default/Deployment/old", DatasourceStatus.PRESENT);
        when(scanner.scan()).thenReturn(List.of());
        when(datasourceRepository.findAll()).thenReturn(List.of(gone));

        DiscoverySummary summary = service.rescan();

        assertThat(summary.markedMissing()).isEqualTo(1);
        assertThat(gone.getStatus()).isEqualTo(DatasourceStatus.MISSING);
    }

    @Test
    void refreshesExistingWorkloadBackToPresent() {
        DatasourceEntity existing = stale("default/Deployment/orders", DatasourceStatus.MISSING);
        when(scanner.scan()).thenReturn(List.of(pgWorkload("orders")));
        when(datasourceRepository.findByDiscoveryKey("default/Deployment/orders"))
                .thenReturn(Optional.of(existing));
        when(datasourceRepository.findAll()).thenReturn(List.of(existing));

        DiscoverySummary summary = service.rescan();

        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.markedMissing()).isZero();
        assertThat(existing.getStatus()).isEqualTo(DatasourceStatus.PRESENT);
    }
}
