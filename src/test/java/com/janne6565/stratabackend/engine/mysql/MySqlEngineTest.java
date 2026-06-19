package com.janne6565.stratabackend.engine.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.engine.ColumnInfo;
import com.janne6565.stratabackend.engine.ObjectRef;
import com.janne6565.stratabackend.engine.QueryMode;
import com.janne6565.stratabackend.engine.QueryResult;
import com.janne6565.stratabackend.engine.RowPage;
import com.janne6565.stratabackend.engine.SchemaInfo;
import com.janne6565.stratabackend.engine.TableInfo;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Exercises the MySQL adapter against a real (Testcontainers) target database. */
@Testcontainers
class MySqlEngineTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    private final MySqlEngine engine = new MySqlEngine();

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @BeforeAll
    static void seed() throws SQLException {
        try (Connection c =
                        DriverManager.getConnection(
                                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE customer (id int AUTO_INCREMENT PRIMARY KEY, name varchar(255) NOT NULL)");
            s.execute("INSERT INTO customer (name) VALUES ('Ada'), ('Linus'), ('Grace')");
        }
    }

    private String database() {
        return MYSQL.getDatabaseName();
    }

    @Test
    void introspectFindsTableWithPrimaryKey() throws SQLException {
        try (Connection c = connection()) {
            SchemaInfo schema = engine.introspect(c);

            TableInfo customer =
                    schema.tables().stream()
                            .filter(t -> t.name().equals("customer"))
                            .findFirst()
                            .orElseThrow();
            assertThat(customer.columns()).extracting(ColumnInfo::name).contains("id", "name");
            ColumnInfo id =
                    customer.columns().stream()
                            .filter(col -> col.name().equals("id"))
                            .findFirst()
                            .orElseThrow();
            assertThat(id.primaryKey()).isTrue();
        }
    }

    @Test
    void browseReturnsRows() throws SQLException {
        try (Connection c = connection()) {
            RowPage page = engine.browse(c, new ObjectRef(database(), "customer"), 0, 2);

            assertThat(page.columns()).contains("id", "name");
            assertThat(page.rows()).hasSize(2);
        }
    }

    @Test
    void readQueryReturnsRows() throws SQLException {
        try (Connection c = connection()) {
            QueryResult result =
                    engine.runQuery(c, "SELECT name FROM customer ORDER BY name", QueryMode.READ);

            assertThat(result.rows()).hasSize(3);
            assertThat(result.rows().get(0).get(0)).isEqualTo("Ada");
        }
    }

    @Test
    void readModeRejectsWrites() throws SQLException {
        try (Connection c = connection()) {
            assertThatThrownBy(
                            () ->
                                    engine.runQuery(
                                            c, "INSERT INTO customer (name) VALUES ('Mallory')", QueryMode.READ))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void writeModeAllowsWrites() throws SQLException {
        try (Connection c = connection()) {
            QueryResult result =
                    engine.runQuery(
                            c, "INSERT INTO customer (name) VALUES ('Dennis')", QueryMode.WRITE);

            assertThat(result.updateCount()).isEqualTo(1);
        }
    }
}
