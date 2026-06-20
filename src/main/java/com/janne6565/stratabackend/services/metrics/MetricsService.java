package com.janne6565.stratabackend.services.metrics;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ResourceMetricsResponse;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.core.GrantEvaluator;
import com.janne6565.stratabackend.services.engine.ConnectionDetailsResolver;
import com.janne6565.stratabackend.services.engine.DatabaseEngine;
import com.janne6565.stratabackend.services.engine.EngineMetrics;
import com.janne6565.stratabackend.services.engine.EngineRegistry;
import com.janne6565.stratabackend.services.metrics.KubernetesMetricsService.PodMetricsSummary;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges the Kubernetes (pod CPU/memory/replicas) and engine (connections/size/objects) telemetry
 * into a {@link ResourceMetricsResponse} per datasource. Both sources are best-effort: a failing or
 * absent source leaves its metrics null rather than failing the whole response. The batch path is
 * scoped to grants (enforcement layer 2) and samples datasources in parallel so a slow database
 * doesn't serialise the whole poll.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final DatasourceRepository datasourceRepository;
    private final GrantEvaluator grantEvaluator;
    private final EngineRegistry engineRegistry;
    private final ConnectionDetailsResolver connectionDetailsResolver;
    private final KubernetesMetricsService kubernetesMetricsService;

    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    /** Single-datasource metrics; authorization is enforced upstream by the policy aspect. */
    @Transactional(readOnly = true)
    public ResourceMetricsResponse metrics(UUID id) {
        DatasourceEntity datasource =
                datasourceRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("Datasource not found: " + id));
        return collect(datasource);
    }

    /** Batch metrics scoped to the datasources the caller may read; unknown ids are skipped. */
    @Transactional(readOnly = true)
    public List<ResourceMetricsResponse> metrics(List<UUID> ids, UserEntity caller) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<DatasourceEntity> readable =
                ids.stream()
                        .distinct()
                        .map(datasourceRepository::findById)
                        .flatMap(Optional::stream)
                        .filter(datasource -> grantEvaluator.canRead(caller, datasource))
                        .toList();
        List<CompletableFuture<ResourceMetricsResponse>> futures =
                readable.stream()
                        .map(
                                datasource ->
                                        CompletableFuture.supplyAsync(
                                                () -> collect(datasource), executor))
                        .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private ResourceMetricsResponse collect(DatasourceEntity datasource) {
        PodMetricsSummary k8s = kubernetesMetricsService.sample(datasource).orElse(null);
        EngineMetrics db = sampleDatabase(datasource);
        return new ResourceMetricsResponse(
                datasource.getId(),
                k8s == null ? null : k8s.cpuPercent(),
                k8s == null ? null : k8s.memoryPercent(),
                k8s == null ? null : k8s.memoryUsageBytes(),
                k8s == null ? null : k8s.podsReady(),
                k8s == null ? null : k8s.podsDesired(),
                db == null ? null : db.connections(),
                db == null ? null : db.dataSizeBytes(),
                db == null ? null : db.objectCount());
    }

    private EngineMetrics sampleDatabase(DatasourceEntity datasource) {
        if (datasource.getDriver() == null || !engineRegistry.supports(datasource.getDriver())) {
            return null;
        }
        try {
            DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
            ConnectionDetails details = connectionDetailsResolver.resolve(datasource);
            return engine.sampleMetrics(details).orElse(null);
        } catch (RuntimeException ex) {
            log.debug("DB metrics unavailable for {}: {}", datasource.getId(), ex.getMessage());
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
