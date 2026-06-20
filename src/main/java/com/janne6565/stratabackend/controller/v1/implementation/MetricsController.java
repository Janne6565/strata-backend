package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.MetricsApi;
import com.janne6565.stratabackend.model.action.MetricsRequest;
import com.janne6565.stratabackend.model.core.ResourceMetricsResponse;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.security.authorization.ResourceId;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.metrics.MetricsService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/** Implements {@link MetricsApi}; reuses {@link Operation#DB_VIEW} (read access) as the gate. */
@RestController
@RequiredArgsConstructor
public class MetricsController implements MetricsApi {

    private final MetricsService metricsService;
    private final CurrentUser currentUser;

    @Override
    @NeedsValidation(Operation.DB_VIEW)
    public ResourceMetricsResponse metrics(@ResourceId UUID id) {
        return metricsService.metrics(id);
    }

    @Override
    public List<ResourceMetricsResponse> metricsBatch(MetricsRequest request) {
        // Batch (collection) endpoint: results scoped to the caller's grants in the service layer,
        // mirroring the inventory list — so no single-resource @NeedsValidation gate here.
        return metricsService.metrics(request.datasourceIds(), currentUser.require());
    }
}
