package com.janne6565.stratabackend.services.engine.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.model.core.BrowseQuery;
import com.janne6565.stratabackend.model.core.ColumnFilter;
import com.janne6565.stratabackend.model.core.ColumnInfo;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.FilterOp;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.SortDirection;
import com.janne6565.stratabackend.model.core.TableInfo;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.janne6565.stratabackend.services.engine.jdbc.JdbcConnectionPool;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
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
    void introspectEstimatesRowCount() throws SQLException {
        // Own table + ANALYZE so reltuples is populated and isolated from sibling writes.
        try (Connection c =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE widget (id serial PRIMARY KEY)");
            s.execute("INSERT INTO widget DEFAULT VALUES");
            s.execute("INSERT INTO widget DEFAULT VALUES");
            s.execute("ANALYZE widget");
        }

        SchemaInfo schema = engine.introspect(details());
        TableInfo widget =
                schema.tables().stream()
                        .filter(t -> t.name().equals("widget"))
                        .findFirst()
                        .orElseThrow();

        assertThat(widget.rowCount()).isEqualTo(2L);
    }

    @Test
    void browseReturnsRows() {
        RowPage page = engine.browse(details(), new ObjectRef("public", "customer"), 0, 2);

        assertThat(page.columns()).contains("id", "name");
        assertThat(page.rows()).hasSize(2);
    }

    @Test
    void browseSortsByColumnDescending() {
        RowPage page =
                engine.browse(
                        details(),
                        new ObjectRef("public", "customer"),
                        new BrowseQuery(0, 50, "name", SortDirection.DESC, List.of()));

        // 'Linus' is the lexicographic max across the seed (and any writes from sibling tests).
        assertThat(page.rows().get(0).get(1)).isEqualTo("Linus");
    }

    @Test
    void browseFiltersByEquality() {
        RowPage page =
                engine.browse(
                        details(),
                        new ObjectRef("public", "customer"),
                        new BrowseQuery(
                                0,
                                50,
                                null,
                                SortDirection.ASC,
                                List.of(new ColumnFilter("name", FilterOp.EQ, "Ada"))));

        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0).get(1)).isEqualTo("Ada");
    }

    @Test
    void browseFiltersByNumericComparison() {
        // String value bound against an integer column — exercises typed parameter binding.
        RowPage page =
                engine.browse(
                        details(),
                        new ObjectRef("public", "customer"),
                        new BrowseQuery(
                                0,
                                50,
                                "id",
                                SortDirection.ASC,
                                List.of(new ColumnFilter("id", FilterOp.GTE, "2"))));

        assertThat(page.rows()).isNotEmpty();
        assertThat(page.rows())
                .allSatisfy(row -> assertThat((Integer) row.get(0)).isGreaterThanOrEqualTo(2));
    }

    @Test
    void browseRejectsUnknownSortColumn() {
        assertThatThrownBy(
                        () ->
                                engine.browse(
                                        details(),
                                        new ObjectRef("public", "customer"),
                                        new BrowseQuery(
                                                0,
                                                50,
                                                "name; DROP TABLE customer",
                                                SortDirection.ASC,
                                                List.of())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void browseRejectsUnknownFilterColumn() {
        assertThatThrownBy(
                        () ->
                                engine.browse(
                                        details(),
                                        new ObjectRef("public", "customer"),
                                        new BrowseQuery(
                                                0,
                                                50,
                                                null,
                                                SortDirection.ASC,
                                                List.of(
                                                        new ColumnFilter(
                                                                "bogus", FilterOp.EQ, "x")))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void readQueryReturnsRows() {
        QueryResult result =
                engine.runQuery(
                        details(), "SELECT name FROM customer ORDER BY name", QueryMode.READ);

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
                        details(),
                        "INSERT INTO customer (name) VALUES ('Dennis')",
                        QueryMode.WRITE);

        assertThat(result.updateCount()).isEqualTo(1);
    }
}
