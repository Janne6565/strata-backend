package com.janne6565.stratabackend.configuration.metrics;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Metric tuning that application.yaml cannot express. Keeps this service inside its Grafana Cloud
 * series budget (see the {@code management.metrics} block in application.yaml).
 */
@Configuration
public class MetricsConfig {

    private static final String ACTUATOR_PREFIX = "/actuator";

    /**
     * Drops the actuator's own traffic (k8s health probes, the Prometheus scrape) from {@code
     * http.server.requests}. Every URI there costs one series per status and percentile, and probe
     * traffic would otherwise dominate the request-rate and latency panels.
     */
    @Bean
    public ObservationPredicate ignoreActuatorRequests() {
        return (name, context) ->
                !(context instanceof ServerRequestObservationContext request
                        && request.getCarrier().getRequestURI().startsWith(ACTUATOR_PREFIX));
    }
}
