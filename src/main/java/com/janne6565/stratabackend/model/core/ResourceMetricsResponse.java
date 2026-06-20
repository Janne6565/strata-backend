package com.janne6565.stratabackend.model.core;

import java.util.UUID;

/**
 * Live resource telemetry for one datasource, merged from two best-effort sources: Kubernetes (pod
 * CPU/memory usage against limits + replica counts, via metrics-server) and the database engine
 * itself (connections, on-disk size, object count). Every metric is nullable — a {@code null} means
 * that source was unavailable (no metrics-server, no backing workload, unreachable database,
 * missing limits), which the UI renders as "—" rather than a zero.
 */
public record ResourceMetricsResponse(
        UUID datasourceId,
        Double cpuPercent,
        Double memoryPercent,
        Long memoryUsageBytes,
        Integer podsReady,
        Integer podsDesired,
        Integer connections,
        Long dataSizeBytes,
        Integer objectCount) {}
