package com.janne6565.stratabackend.services.engine;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.AuditOutcome;
import com.janne6565.stratabackend.model.core.BrowseQuery;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.core.AuditService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Drives the engine for a datasource: resolves the adapter and the live connection details, runs
 * the operation, and audits the outcome. Authorization is enforced upstream by the policy aspect;
 * the engine enforces read-only at the driver level for {@link QueryMode#READ} (defence-in-depth).
 */
@Service
@RequiredArgsConstructor
public class BrowseService {

    private static final int MAX_BROWSE_LIMIT = 500;

    private final ConnectionDetailsResolver connectionDetailsResolver;
    private final EngineRegistry engineRegistry;
    private final DatasourceRepository datasourceRepository;
    private final AuditService auditService;

    public SchemaInfo schema(UUID datasourceId) {
        DatasourceEntity datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        ConnectionDetails details = connectionDetailsResolver.resolve(datasource);
        return engine.introspect(details);
    }

    public RowPage browse(
            UUID datasourceId, String schema, String table, BrowseQuery query, UserEntity caller) {
        DatasourceEntity datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        ConnectionDetails details = connectionDetailsResolver.resolve(datasource);
        int cappedLimit = Math.min(Math.max(query.limit(), 0), MAX_BROWSE_LIMIT);
        String target = schema + "." + table;
        try {
            RowPage page =
                    engine.browse(
                            details, new ObjectRef(schema, table), query.withLimit(cappedLimit));
            audit(caller, datasource, "DB_BROWSE", target, AuditOutcome.SUCCESS, null);
            return page;
        } catch (EngineException ex) {
            audit(caller, datasource, "DB_BROWSE", target, AuditOutcome.FAILURE, ex.getMessage());
            throw ex;
        }
    }

    public QueryResult query(UUID datasourceId, String sql, QueryMode mode, UserEntity caller) {
        DatasourceEntity datasource = require(datasourceId);
        DatabaseEngine engine = engineRegistry.forDriver(datasource.getDriver());
        ConnectionDetails details = connectionDetailsResolver.resolve(datasource);
        String operation = mode == QueryMode.WRITE ? "DB_QUERY_WRITE" : "DB_QUERY_READ";
        try {
            QueryResult result = engine.runQuery(details, sql, mode);
            audit(
                    caller,
                    datasource,
                    operation,
                    datasource.getDisplayName(),
                    AuditOutcome.SUCCESS,
                    sql);
            return result;
        } catch (EngineException ex) {
            audit(
                    caller,
                    datasource,
                    operation,
                    datasource.getDisplayName(),
                    AuditOutcome.FAILURE,
                    sql);
            throw ex;
        }
    }

    private void audit(
            UserEntity caller,
            DatasourceEntity datasource,
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

    private DatasourceEntity require(UUID id) {
        return datasourceRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("DatasourceEntity not found: " + id));
    }
}
