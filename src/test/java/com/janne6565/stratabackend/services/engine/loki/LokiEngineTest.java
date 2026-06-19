package com.janne6565.stratabackend.services.engine.loki;

import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the Loki adapter against a real (Testcontainers) target instance. */
@Testcontainers
class LokiEngineTest {

    @Container
    static final GenericContainer<?> LOKI =
            new GenericContainer<>(DockerImageName.parse("grafana/loki:2.9.0"))
                    .withExposedPorts(3100)
                    .waitingFor(
                            Wait.forHttp("/ready")
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofSeconds(120)));

    private final LokiEngine engine = new LokiEngine();

    private ConnectionDetails details() {
        return new ConnectionDetails(
                "loki", LOKI.getHost(), LOKI.getMappedPort(3100), null, null, null);
    }

    @BeforeAll
    static void seed() throws Exception {
        long now = System.currentTimeMillis() * 1_000_000L;
        String body =
                "{\"streams\":[{\"stream\":{\"app\":\"web\"},\"values\":["
                        + "[\"" + now + "\",\"hello from web\"],"
                        + "[\"" + (now + 1_000_000L) + "\",\"second line\"]]}]}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://"
                                                + LOKI.getHost()
                                                + ":"
                                                + LOKI.getMappedPort(3100)
                                                + "/loki/api/v1/push"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        for (int attempt = 0; attempt < 15; attempt++) {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Loki did not accept seed log lines");
    }

    @Test
    void introspectListsLabels() {
        SchemaInfo schema = engine.introspect(details());

        assertThat(schema.tables()).hasSize(1);
        assertThat(schema.tables().get(0).columns()).extracting(c -> c.name()).contains("line", "app");
    }

    @Test
    void browseReturnsLogLines() {
        RowPage page = engine.browse(details(), new ObjectRef("loki", "logs"), 0, 10);

        assertThat(page.columns()).contains("timestamp", "line");
        assertThat(page.rows()).isNotEmpty();
        assertThat(page.rows()).extracting(r -> r.get(1)).contains("hello from web");
    }

    @Test
    void readLogqlReturnsRows() {
        QueryResult result = engine.runQuery(details(), "{app=\"web\"}", QueryMode.READ);

        assertThat(result.columns()).contains("line");
        assertThat(result.rows()).isNotEmpty();
    }

    @Test
    void writeModeIsRejected() {
        assertThatThrownBy(() -> engine.runQuery(details(), "{app=\"web\"}", QueryMode.WRITE))
                .isInstanceOf(EngineException.class);
    }
}
