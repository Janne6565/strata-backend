package com.janne6565.stratabackend.engine.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.common.EngineException;
import com.janne6565.stratabackend.engine.ColumnInfo;
import com.janne6565.stratabackend.engine.ConnectionDetails;
import com.janne6565.stratabackend.engine.ObjectRef;
import com.janne6565.stratabackend.engine.QueryMode;
import com.janne6565.stratabackend.engine.QueryResult;
import com.janne6565.stratabackend.engine.RowPage;
import com.janne6565.stratabackend.engine.SchemaInfo;
import com.janne6565.stratabackend.engine.TableInfo;
import com.janne6565.stratabackend.engine.jdbc.JdbcConnectionPool;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Exercises the PostgreSQL adapter against a real (Testcontainers) target database. */
@Testcontainers
class PostgresEngineTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private final PostgresEngine engine = new PostgresEngine(new JdbcConnectionPool());

    private ConnectionDetails details() {
        return new ConnectionDetails(
                "postgresql",
                POSTGRES.getHost(),
                POSTGRES.getFirstMappedPort(),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    @BeforeAll
    static void seed() throws SQLException {
        try (Connection c =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE customer (id serial PRIMARY KEY, name text NOT NULL)");
            s.execute("INSERT INTO customer (name) VALUES ('Ada'), ('Linus'), ('Grace')");
        }
    }

    @Test
    void introspectFindsTheTableWithColumnsAndPrimaryKey() {
        SchemaInfo schema = engine.introspect(details());

        TableInfo customer =
                schema.tables().stream()
                        .filter(t -> t.name().equals("customer"))
                        .findFirst()
                        .orElseThrow();
        assertThat(customer.schema()).isEqualTo("public");
        assertThat(customer.columns()).extracting(ColumnInfo::name).contains("id", "name");
        ColumnInfo id =
                customer.columns().stream()
                        .filter(col -> col.name().equals("id"))
                        .findFirst()
                        .orElseThrow();
        assertThat(id.primaryKey()).isTrue();
    }

    @Test
    void browseReturnsRows() {
        RowPage page = engine.browse(details(), new ObjectRef("public", "customer"), 0, 2);

        assertThat(page.columns()).contains("id", "name");
        assertThat(page.rows()).hasSize(2);
    }

    @Test
    void readQueryReturnsRows() {
        QueryResult result =
                engine.runQuery(details(), "SELECT name FROM customer ORDER BY name", QueryMode.READ);

        assertThat(result.columns()).containsExactly("name");
        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows().get(0).get(0)).isEqualTo("Ada");
    }

    @Test
    void readModeRejectsWrites() {
        assertThatThrownBy(
                        () ->
                                engine.runQuery(
                                        details(),
                                        "INSERT INTO customer (name) VALUES ('Mallory')",
                                        QueryMode.READ))
                .isInstanceOf(EngineException.class);
    }

    @Test
    void writeModeAllowsWrites() {
        QueryResult result =
                engine.runQuery(
                        details(), "INSERT INTO customer (name) VALUES ('Dennis')", QueryMode.WRITE);

        assertThat(result.updateCount()).isEqualTo(1);
    }
}
