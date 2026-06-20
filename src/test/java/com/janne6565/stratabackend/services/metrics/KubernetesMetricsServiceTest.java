package com.janne6565.stratabackend.services.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KubernetesMetricsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode summary(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void sumsUsedBytesOfPvcVolumesForMatchingPodsOnly() throws Exception {
        JsonNode summary =
                summary(
                        """
                        {"pods":[
                          {"podRef":{"name":"postgres-0","namespace":"cosy"},
                           "volume":[
                             {"name":"data","usedBytes":1048576,"pvcRef":{"name":"data-postgres-0"}},
                             {"name":"tmp","usedBytes":4096}
                           ]},
                          {"podRef":{"name":"other-0","namespace":"cosy"},
                           "volume":[{"name":"data","usedBytes":999,"pvcRef":{"name":"x"}}]}
                        ]}
                        """);

        Long bytes =
                KubernetesMetricsService.sumPvcUsedBytes(summary, "cosy", Set.of("postgres-0"));

        // Only postgres-0's PVC volume counts; the emptyDir 'tmp' and the other pod are excluded.
        assertThat(bytes).isEqualTo(1_048_576L);
    }

    @Test
    void sumsAcrossMultipleMatchingPods() throws Exception {
        JsonNode summary =
                summary(
                        """
                        {"pods":[
                          {"podRef":{"name":"loki-0","namespace":"cosy"},
                           "volume":[{"name":"d","usedBytes":100,"pvcRef":{"name":"a"}}]},
                          {"podRef":{"name":"loki-1","namespace":"cosy"},
                           "volume":[{"name":"d","usedBytes":250,"pvcRef":{"name":"b"}}]}
                        ]}
                        """);

        Long bytes =
                KubernetesMetricsService.sumPvcUsedBytes(
                        summary, "cosy", Set.of("loki-0", "loki-1"));

        assertThat(bytes).isEqualTo(350L);
    }

    @Test
    void returnsNullWhenNoPvcVolumeMatches() throws Exception {
        JsonNode summary =
                summary(
                        """
                        {"pods":[
                          {"podRef":{"name":"redis-0","namespace":"cosy"},
                           "volume":[{"name":"tmp","usedBytes":4096}]}
                        ]}
                        """);

        // No pvcRef on the only volume, and the wrong namespace is ignored too.
        assertThat(KubernetesMetricsService.sumPvcUsedBytes(summary, "cosy", Set.of("redis-0")))
                .isNull();
        assertThat(KubernetesMetricsService.sumPvcUsedBytes(summary, "other", Set.of("redis-0")))
                .isNull();
    }
}
