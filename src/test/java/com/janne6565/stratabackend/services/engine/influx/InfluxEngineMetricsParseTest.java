package com.janne6565.stratabackend.services.engine.influx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for parsing InfluxDB's Prometheus {@code /metrics} (no container needed). */
class InfluxEngineMetricsParseTest {

    private static final String METRICS =
            """
            # HELP storage_shard_disk_size Gauge of the disk size for the shard
            storage_shard_disk_size{bucket="aaa",engine="tsm1",id="1"} 9.364395e+06
            storage_shard_disk_size{bucket="aaa",engine="tsm1",id="2"} 1.203259e+07
            storage_shard_disk_size{bucket="bbb",engine="tsm1",id="1"} 500
            storage_bucket_measurement_num{bucket="aaa"} 3
            storage_bucket_series_num{bucket="aaa"} 11
            """;

    @Test
    void sumsShardDiskSizeAcrossTheTargetBucketsShards() {
        Long bytes = InfluxEngine.sumMetricForBucket(METRICS, "storage_shard_disk_size", "aaa");
        // 9_364_395 + 12_032_590, and the other bucket's shard is excluded.
        assertThat(bytes).isEqualTo(21_396_985L);
    }

    @Test
    void readsSingleValueGaugeLikeMeasurementCount() {
        assertThat(
                        InfluxEngine.sumMetricForBucket(
                                METRICS, "storage_bucket_measurement_num", "aaa"))
                .isEqualTo(3L);
    }

    @Test
    void returnsNullWhenTheBucketHasNoMatchingSeries() {
        assertThat(InfluxEngine.sumMetricForBucket(METRICS, "storage_shard_disk_size", "zzz"))
                .isNull();
    }
}
