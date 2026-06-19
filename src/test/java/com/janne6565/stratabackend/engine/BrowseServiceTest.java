package com.janne6565.stratabackend.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.audit.AuditOutcome;
import com.janne6565.stratabackend.audit.AuditService;
import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.BadRequestException;
import com.janne6565.stratabackend.engine.postgres.PostgresEngine;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Exercises the full service→engine→audit path against a real (Testcontainers) target Postgres. */
@Testcontainers
class BrowseServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private final DatasourceRepository datasourceRepository = mock(DatasourceRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private BrowseService service;
    private Datasource datasource;
    private final User caller = new User("dev", "hash", Role.USER);

    @BeforeAll
    static void seed() throws SQLException {
        try (Connection c = open();
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE customer (id serial PRIMARY KEY, name text NOT NULL)");
            s.execute("INSERT INTO customer (name) VALUES ('Ada'), ('Linus')");
        }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeEach
    void setUp() {
        ConnectionProvider provider =
                ds -> open(); // bypasses the Kubernetes credential machinery
        EngineRegistry registry = new EngineRegistry(List.of(new PostgresEngine()));
        service = new BrowseService(provider, registry, datasourceRepository, auditService);

        datasource = new Datasource();
        datasource.setId(UUID.randomUUID());
        datasource.setDriver("postgresql");
        datasource.setNamespace("default");
        datasource.setDisplayName("orders");
        when(datasourceRepository.findById(datasource.getId())).thenReturn(Optional.of(datasource));
    }

    @Test
    void schemaListsTables() {
        SchemaInfo schema = service.schema(datasource.getId());

        assertThat(schema.tables()).extracting(TableInfo::name).contains("customer");
    }

    @Test
    void browseReturnsRowsAndAudits() {
        RowPage page = service.browse(datasource.getId(), "public", "customer", 0, 10, caller);

        assertThat(page.columns()).contains("id", "name");
        assertThat(page.rows()).isNotEmpty();
        verify(auditService)
                .record(eq(caller.getId()), eq("DB_BROWSE"), any(), eq("default"), eq(AuditOutcome.SUCCESS), any());
    }

    @Test
    void readQueryReturnsRows() {
        QueryResult result =
                service.query(datasource.getId(), "SELECT name FROM customer", QueryMode.READ, caller);

        assertThat(result.columns()).containsExactly("name");
        assertThat(result.rows()).isNotEmpty();
    }

    @Test
    void readQueryRejectingWriteIsAuditedAsFailure() {
        assertThatThrownBy(
                        () ->
                                service.query(
                                        datasource.getId(),
                                        "INSERT INTO customer (name) VALUES ('x')",
                                        QueryMode.READ,
                                        caller))
                .isInstanceOf(BadRequestException.class);
        verify(auditService)
                .record(any(), eq("DB_QUERY_READ"), any(), any(), eq(AuditOutcome.FAILURE), any());
    }

    @Test
    void writeExecuteInsertsAndAudits() {
        QueryResult result =
                service.query(
                        datasource.getId(),
                        "INSERT INTO customer (name) VALUES ('Grace')",
                        QueryMode.WRITE,
                        caller);

        assertThat(result.updateCount()).isEqualTo(1);
        verify(auditService)
                .record(any(), eq("DB_QUERY_WRITE"), any(), any(), eq(AuditOutcome.SUCCESS), any());
    }
}
