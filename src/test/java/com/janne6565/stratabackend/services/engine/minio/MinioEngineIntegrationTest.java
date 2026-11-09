package com.janne6565.stratabackend.services.engine.minio;

import static org.assertj.core.api.Assertions.assertThat;

import com.janne6565.stratabackend.model.core.BrowseQuery;
import com.janne6565.stratabackend.model.core.ColumnFilter;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.FilterOp;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.TableInfo;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the MinIO adapter against a real (Testcontainers) MinIO, which validates the SigV4 signing
 * end to end. Seeds with the {@code mc} client bundled in the server image.
 */
@Testcontainers
class MinioEngineIntegrationTest {

    private static final String USER = "strata-root";
    private static final String PASSWORD = "strata-secret-123";

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(
                            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
                    .withEnv("MINIO_ROOT_USER", USER)
                    .withEnv("MINIO_ROOT_PASSWORD", PASSWORD)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(
                            Wait.forHttp("/minio/health/ready")
                                    .forPort(9000)
                                    .withStartupTimeout(Duration.ofSeconds(120)));

    private final MinioEngine engine = new MinioEngine();

    @BeforeAll
    static void seed() throws Exception {
        sh("mc alias set local http://127.0.0.1:9000 " + USER + " " + PASSWORD);
        sh("mc mb local/photos local/empty");
        sh("head -c 1000 /dev/zero > /tmp/a.jpg && head -c 2500 /dev/zero > /tmp/b.txt");
        sh("mc cp /tmp/a.jpg 'local/photos/2024/my photo.jpg'");
        sh("mc cp /tmp/b.txt local/photos/notes.txt");
    }

    private static void sh(String command) throws Exception {
        ExecResult result = MINIO.execInContainer("sh", "-c", command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(command + " failed: " + result.getStderr());
        }
    }

    private ConnectionDetails details() {
        return new ConnectionDetails(
                "minio", MINIO.getHost(), MINIO.getMappedPort(9000), USER, USER, PASSWORD);
    }

    @Test
    void introspectsBucketsWithUsage() {
        List<TableInfo> tables = engine.introspect(details()).tables();

        assertThat(tables).extracting(TableInfo::name).containsExactly("empty", "photos");
        TableInfo photos = tables.get(1);
        assertThat(photos.rowCount()).isEqualTo(2L);
        assertThat(photos.sizeBytes()).isEqualTo(3_500L);
    }

    @Test
    void browsesFoldersAndObjects() {
        RowPage root = engine.browse(details(), new ObjectRef("minio", "photos"), 0, 50);
        assertThat(root.rows()).extracting(r -> r.get(0)).containsExactly("2024/", "notes.txt");
        assertThat(root.rows().get(1).get(3)).isEqualTo(2_500L);

        RowPage folder =
                engine.browse(
                        details(),
                        new ObjectRef("minio", "photos"),
                        new BrowseQuery(
                                0,
                                50,
                                null,
                                null,
                                List.of(new ColumnFilter("prefix", FilterOp.EQ, "2024/"))));
        assertThat(folder.rows()).extracting(r -> r.get(1)).containsExactly("2024/my photo.jpg");
    }

    @Test
    void statsAnObjectWithSpacesInItsKey() {
        QueryResult stat =
                engine.runQuery(details(), "stat photos/2024/my photo.jpg", QueryMode.READ);

        assertThat(stat.rows()).contains(List.of("content-length", "1000"));
    }

    @Test
    void reportsTotalStorage() {
        assertThat(engine.sampleMetrics(details()).orElseThrow().dataSizeBytes()).isEqualTo(3_500L);
    }
}
