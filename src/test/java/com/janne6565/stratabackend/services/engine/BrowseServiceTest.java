package com.janne6565.stratabackend.services.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.AuditOutcome;
import com.janne6565.stratabackend.model.core.BrowseQuery;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.TableInfo;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.core.AuditService;
import com.janne6565.stratabackend.services.engine.jdbc.JdbcConnectionPool;
import com.janne6565.stratabackend.services.engine.postgres.PostgresEngine;
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
    private DatasourceEntity datasource;
    private final UserEntity caller = new UserEntity("dev", "hash", Role.USER);

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
        // Resolve straight to the container — bypasses the Kubernetes credential machinery.
        ConnectionDetailsResolver resolver =
                ds ->
                        new ConnectionDetails(
                                "postgresql",
                                POSTGRES.getHost(),
                                POSTGRES.getFirstMappedPort(),
                                POSTGRES.getDatabaseName(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
        EngineRegistry registry =
                new EngineRegistry(List.of(new PostgresEngine(new JdbcConnectionPool())));
        service = new BrowseService(resolver, registry, datasourceRepository, auditService);

        datasource = new DatasourceEntity();
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
        RowPage page =
                service.browse(
                        datasource.getId(), "public", "customer", BrowseQuery.paged(0, 10), caller);

        assertThat(page.columns()).contains("id", "name");
        assertThat(page.rows()).isNotEmpty();
        verify(auditService)
                .record(
                        eq(caller.getId()),
                        eq("DB_BROWSE"),
                        any(),
                        eq("default"),
                        eq(AuditOutcome.SUCCESS),
                        any());
    }

    @Test
    void readQueryReturnsRows() {
        QueryResult result =
                service.query(
                        datasource.getId(), "SELECT name FROM customer", QueryMode.READ, caller);

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
