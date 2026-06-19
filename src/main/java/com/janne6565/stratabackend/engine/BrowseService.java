package com.janne6565.stratabackend.engine;

import com.janne6565.stratabackend.audit.AuditOutcome;
import com.janne6565.stratabackend.audit.AuditService;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.EngineException;
import com.janne6565.stratabackend.common.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Drives the engine for a datasource: resolves the adapter and the live connection details, runs
 * the operation, and audits the outcome. Authorization is enforced upstream by the policy aspect;
 * the engine enforces read-only at the driver level for {@link QueryMode#READ} (defence-in-depth).
 */
@Service
public class BrowseService {

    private static final int MAX_BROWSE_LIMIT = 500;

    private final ConnectionDetailsResolver connectionDetailsResolver;
    private final EngineRegistry engineRegistry;
    private final DatasourceRepository datasourceRepository;
    private final AuditService auditService;

    public BrowseService(
            ConnectionDetailsResolver connectionDetailsResolver,
            EngineRegistry engineRegistry,
            DatasourceRepository datasourceRepository,
            AuditService auditService) {
        this.connectionDetailsResolver = connectionDetailsResolver;
        this.engineRegistry = engineRegistry;
        this.datasourceRepository = datasourceRepository;
        this.auditService = auditService;
    }

    public SchemaInfo schema(UUID datasourceId) {
        Datasource datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        ConnectionDetails details = connectionDetailsResolver.resolve(datasource);
        return engine.introspect(details);
    }

    public RowPage browse(
            UUID datasourceId, String schema, String table, int offset, int limit, User caller) {
        Datasource datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        ConnectionDetails details = connectionDetailsResolver.resolve(datasource);
        int cappedLimit = Math.min(Math.max(limit, 0), MAX_BROWSE_LIMIT);
        String target = schema + "." + table;
        try {
            RowPage page = engine.browse(details, new ObjectRef(schema, table), offset, cappedLimit);
            audit(caller, datasource, "DB_BROWSE", target, AuditOutcome.SUCCESS, null);
            return page;
        } catch (EngineException ex) {
            audit(caller, datasource, "DB_BROWSE", target, AuditOutcome.FAILURE, ex.getMessage());
            throw ex;
        }
    }

    public QueryResult query(UUID datasourceId, String sql, QueryMode mode, User caller) {
        Datasource datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        ConnectionDetails details = connectionDetailsResolver.resolve(datasource);
        String operation = mode == QueryMode.WRITE ? "DB_QUERY_WRITE" : "DB_QUERY_READ";
        try {
            QueryResult result = engine.runQuery(details, sql, mode);
            audit(caller, datasource, operation, datasource.getDisplayName(), AuditOutcome.SUCCESS, sql);
            return result;
        } catch (EngineException ex) {
            audit(caller, datasource, operation, datasource.getDisplayName(), AuditOutcome.FAILURE, sql);
            throw ex;
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
}
