package com.janne6565.stratabackend.model.action;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Batch request for datasource resource metrics. The caller supplies the datasource ids it wants
 * telemetry for (typically the rows currently on screen); the service drops any the caller may not
 * read, so the response only covers permitted datasources.
 */
public record MetricsRequest(@NotNull List<UUID> datasourceIds) {}
