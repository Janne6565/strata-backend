package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.action.MetricsRequest;
import com.janne6565.stratabackend.model.core.ResourceMetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Live resource-metrics contract for datasources. The batch endpoint backs the polling list view;
 * the single endpoint backs a datasource detail page. Authorization enforced on the implementation.
 */
@Tag(name = "Metrics")
@RequestMapping(path = "/v1/datasources", produces = MediaType.APPLICATION_JSON_VALUE)
public interface MetricsApi {

    @Operation(summary = "Resource metrics for a single datasource")
    @GetMapping("/{id}/metrics")
    ResourceMetricsResponse metrics(@PathVariable UUID id);

    @Operation(
            summary = "Resource metrics for multiple datasources (scoped to the caller's grants)")
    @PostMapping(path = "/metrics", consumes = MediaType.APPLICATION_JSON_VALUE)
    List<ResourceMetricsResponse> metricsBatch(@Valid @RequestBody MetricsRequest request);
}
