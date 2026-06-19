package com.janne6565.stratabackend.engine.influx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.janne6565.stratabackend.common.EngineException;
import com.janne6565.stratabackend.engine.ConnectionDetails;
import com.janne6565.stratabackend.engine.ObjectRef;
import com.janne6565.stratabackend.engine.QueryMode;
import com.janne6565.stratabackend.engine.QueryResult;
import com.janne6565.stratabackend.engine.RowPage;
import com.janne6565.stratabackend.engine.SchemaInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Exercises the InfluxDB 2.x adapter against a real (Testcontainers) target instance. */
@Testcontainers
class InfluxEngineTest {

    private static final String ORG = "test-org";
    private static final String BUCKET = "test-bucket";
    private static final String TOKEN = "test-token-123";

    @Container
    static final GenericContainer<?> INFLUX =
            new GenericContainer<>(DockerImageName.parse("influxdb:2.7"))
                    .withExposedPorts(8086)
                    .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                    .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "admin")
                    .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "password123")
                    .withEnv("DOCKER_INFLUXDB_INIT_ORG", ORG)
                    .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", BUCKET)
                    .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", TOKEN)
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    private final InfluxEngine engine = new InfluxEngine();

    private ConnectionDetails details() {
        return new ConnectionDetails(
                "influxdb", INFLUX.getHost(), INFLUX.getMappedPort(8086), BUCKET, ORG, TOKEN);
    }

    @BeforeAll
    static void seed() {
        try (InfluxDBClient client =
                InfluxDBClientFactory.create(
                        "http://" + INFLUX.getHost() + ":" + INFLUX.getMappedPort(8086),
                        TOKEN.toCharArray(),
                        ORG,
                        BUCKET)) {
            WriteApiBlocking write = client.getWriteApiBlocking();
            write.writeRecords(
                    WritePrecision.NS,
                    List.of("weather,location=us temp=72", "weather,location=eu temp=15"));
        }
    }

    @Test
    void introspectListsMeasurementWithFieldColumns() {
        SchemaInfo schema = engine.introspect(details());

        assertThat(schema.tables()).extracting(t -> t.name()).contains("weather");
        assertThat(
                        schema.tables().stream()
                                .filter(t -> t.name().equals("weather"))
                                .findFirst()
                                .orElseThrow()
                                .columns())
                .extracting(c -> c.name())
                .contains("temp");
    }

    @Test
    void browseReturnsPoints() {
        RowPage page = engine.browse(details(), new ObjectRef(BUCKET, "weather"), 0, 10);

        assertThat(page.columns()).contains("_measurement", "_field", "_value");
        assertThat(page.rows()).isNotEmpty();
    }

    @Test
    void readFluxReturnsRows() {
        String flux =
                "from(bucket: \"" + BUCKET + "\") |> range(start: 0) "
                        + "|> filter(fn: (r) => r._measurement == \"weather\")";

        QueryResult result = engine.runQuery(details(), flux, QueryMode.READ);

        assertThat(result.rows()).isNotEmpty();
    }

    @Test
    void readModeRejectsWriteFunction() {
        String flux =
                "from(bucket: \"" + BUCKET + "\") |> range(start: 0) "
                        + "|> to(bucket: \"other\")";

        assertThatThrownBy(() -> engine.runQuery(details(), flux, QueryMode.READ))
                .isInstanceOf(EngineException.class);
    }

    @Test
    void writeModeIngestsLineProtocol() {
        QueryResult result =
                engine.runQuery(details(), "weather,location=apac temp=30", QueryMode.WRITE);

        assertThat(result.updateCount()).isEqualTo(1);
    }
}
