package com.janne6565.stratabackend.configuration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.janne6565.stratabackend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Prometheus scrape endpoint is reachable without credentials (Grafana Alloy scrapes it
 * in-cluster) and publishes the lean meter set configured in application.yaml: request timers with
 * client-side percentiles, no bucket histogram, and the disabled families absent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PrometheusEndpointTest {

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";

    @Autowired private MockMvc mockMvc;

    @Test
    void scrapeIsPublicAndLean() throws Exception {
        // An API request records an http.server.requests sample; the first scrape must not.
        mockMvc.perform(get("/api/v1/datasources")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROMETHEUS_PATH)).andExpect(status().isOk());

        String body =
                mockMvc.perform(get(PROMETHEUS_PATH))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body)
                .contains("http_server_requests_seconds_count")
                .contains("quantile=\"0.95\"")
                .contains("quantile=\"0.99\"")
                .contains("application=\"strata-backend\"")
                .doesNotContain("_bucket")
                .doesNotContain("uri=\"/actuator")
                .doesNotContain("quantile=\"0.5\"")
                .doesNotContain("spring_security_")
                .doesNotContain("jvm_classes_")
                .doesNotContain("http_server_requests_active_");
    }

    @Test
    void otherActuatorEndpointsStayProtected() throws Exception {
        mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
    }
}
