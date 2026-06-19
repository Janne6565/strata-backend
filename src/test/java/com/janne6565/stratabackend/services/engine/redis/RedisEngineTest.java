package com.janne6565.stratabackend.services.engine.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Exercises the Redis adapter against a real (Testcontainers) target instance. */
@Testcontainers
class RedisEngineTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private final RedisEngine engine = new RedisEngine();

    private ConnectionDetails details() {
        return new ConnectionDetails(
                "redis", REDIS.getHost(), REDIS.getMappedPort(6379), "0", null, null);
    }

    @BeforeAll
    static void seed() {
        RedisURI uri =
                RedisURI.builder()
                        .withHost(REDIS.getHost())
                        .withPort(REDIS.getMappedPort(6379))
                        .build();
        try (RedisClient client = RedisClient.create(uri);
                StatefulRedisConnection<String, String> connection = client.connect()) {
            RedisCommands<String, String> sync = connection.sync();
            sync.set("user:1", "Ada");
            sync.set("user:2", "Linus");
            sync.hset("profile:1", java.util.Map.of("name", "Ada", "role", "admin"));
        }
    }

    @Test
    void introspectExposesTheKeyspaceObject() {
        SchemaInfo schema = engine.introspect(details());

        assertThat(schema.tables()).hasSize(1);
        assertThat(schema.tables().get(0).name()).isEqualTo("keys");
        assertThat(schema.tables().get(0).columns())
                .extracting(c -> c.name())
                .contains("key", "type", "ttl");
    }

    @Test
    void browseListsKeysWithType() {
        RowPage page = engine.browse(details(), new ObjectRef("db0", "keys"), 0, 10);

        assertThat(page.columns()).containsExactly("key", "type", "ttl");
        assertThat(page.rows()).extracting(r -> r.get(0)).contains("user:1", "user:2", "profile:1");
    }

    @Test
    void readGetReturnsValue() {
        QueryResult result = engine.runQuery(details(), "GET user:1", QueryMode.READ);

        assertThat(result.columns()).containsExactly("value");
        assertThat(result.rows().get(0).get(0)).isEqualTo("Ada");
    }

    @Test
    void readHgetallReturnsFields() {
        QueryResult result = engine.runQuery(details(), "HGETALL profile:1", QueryMode.READ);

        assertThat(result.rows())
                .extracting(r -> r.get(0))
                .contains("name", "Ada", "role", "admin");
    }

    @Test
    void readModeRejectsWriteCommand() {
        assertThatThrownBy(() -> engine.runQuery(details(), "SET user:3 Mallory", QueryMode.READ))
                .isInstanceOf(EngineException.class);
    }

    @Test
    void writeModeRunsWriteCommand() {
        QueryResult result = engine.runQuery(details(), "SET user:9 Dennis", QueryMode.WRITE);

        assertThat(result.rows().get(0).get(0)).isEqualTo("OK");
    }
}
