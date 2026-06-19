package com.janne6565.stratabackend.engine;

import com.janne6565.stratabackend.audit.AuditOutcome;
import com.janne6565.stratabackend.audit.AuditService;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.BadRequestException;
import com.janne6565.stratabackend.common.NotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Drives the engine for a datasource: resolves the adapter, borrows a pooled connection, runs the
 * operation, and audits the outcome. Authorization is enforced upstream by the policy aspect; the
 * engine enforces read-only at the driver level for {@link QueryMode#READ} (defence-in-depth).
 */
@Service
public class BrowseService {

    private static final int MAX_BROWSE_LIMIT = 500;

    private final ConnectionProvider connectionProvider;
    private final EngineRegistry engineRegistry;
    private final DatasourceRepository datasourceRepository;
    private final AuditService auditService;

    public BrowseService(
            ConnectionProvider connectionProvider,
            EngineRegistry engineRegistry,
            DatasourceRepository datasourceRepository,
            AuditService auditService) {
        this.connectionProvider = connectionProvider;
        this.engineRegistry = engineRegistry;
        this.datasourceRepository = datasourceRepository;
        this.auditService = auditService;
    }

    public SchemaInfo schema(UUID datasourceId) {
        Datasource datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        try (Connection connection = connectionProvider.getConnection(datasource)) {
            return engine.introspect(connection);
        } catch (SQLException ex) {
            throw engineError(ex);
        }
    }

    public RowPage browse(
            UUID datasourceId, String schema, String table, int offset, int limit, User caller) {
        Datasource datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        int cappedLimit = Math.min(Math.max(limit, 0), MAX_BROWSE_LIMIT);
        String target = schema + "." + table;
        try (Connection connection = connectionProvider.getConnection(datasource)) {
            RowPage page = engine.browse(connection, new ObjectRef(schema, table), offset, cappedLimit);
            audit(caller, datasource, "DB_BROWSE", target, AuditOutcome.SUCCESS, null);
            return page;
        } catch (SQLException ex) {
            audit(caller, datasource, "DB_BROWSE", target, AuditOutcome.FAILURE, ex.getMessage());
            throw engineError(ex);
        }
    }

    public QueryResult query(UUID datasourceId, String sql, QueryMode mode, User caller) {
        Datasource datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        String operation = mode == QueryMode.WRITE ? "DB_QUERY_WRITE" : "DB_QUERY_READ";
        try (Connection connection = connectionProvider.getConnection(datasource)) {
            QueryResult result = engine.runQuery(connection, sql, mode);
            audit(caller, datasource, operation, datasource.getDisplayName(), AuditOutcome.SUCCESS, sql);
            return result;
        } catch (SQLException ex) {
            audit(caller, datasource, operation, datasource.getDisplayName(), AuditOutcome.FAILURE, sql);
            throw engineError(ex);
        }
    }

    private void audit(
            User caller,
            Datasource datasource,
            String operation,
            String target,
            AuditOutcome outcome,
            String summary) {
        auditService.record(
                caller.getId(),
                operation,
                datasource.getId() + ":" + target,
                datasource.getNamespace(),
                outcome,
                summary);
    }

    private Datasource require(UUID id) {
        return datasourceRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Datasource not found: " + id));
    }

    private RuntimeException engineError(SQLException ex) {
        return new BadRequestException("Query failed: " + ex.getMessage());
    }
}
