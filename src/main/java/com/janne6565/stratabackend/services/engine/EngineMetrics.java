package com.janne6565.stratabackend.services.engine;

/**
 * Engine-sourced telemetry sampled live from a database (the DB side of {@code
 * ResourceMetricsResponse}). Each field is nullable when the engine can't determine it: {@code
 * connections} (active sessions/clients), {@code dataSizeBytes} (on-disk/in-memory size) and {@code
 * objectCount} (tables/collections/keys). Produced by {@link DatabaseEngine#sampleMetrics}.
 */
public record EngineMetrics(Integer connections, Long dataSizeBytes, Integer objectCount) {}
