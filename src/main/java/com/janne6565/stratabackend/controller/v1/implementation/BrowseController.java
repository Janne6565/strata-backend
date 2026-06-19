package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.BrowseApi;
import com.janne6565.stratabackend.model.action.QueryRequest;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.security.authorization.ResourceId;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.engine.BrowseService;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements {@link BrowseApi}. Read operations are gated by DB_BROWSE/DB_QUERY_READ; the separate
 * /execute endpoint is gated by DB_QUERY_WRITE so write authorization (writable grant + prod
 * safe-mode) is decided by the policy aspect, not by parsing SQL.
 */
@RestController
public class BrowseController implements BrowseApi {

    private final BrowseService browseService;
    private final CurrentUser currentUser;

    public BrowseController(BrowseService browseService, CurrentUser currentUser) {
        this.browseService = browseService;
        this.currentUser = currentUser;
    }

    @Override
    @NeedsValidation(Operation.DB_BROWSE)
    public SchemaInfo schema(@ResourceId UUID id) {
        return browseService.schema(id);
    }

    @Override
    @NeedsValidation(Operation.DB_BROWSE)
    public RowPage browse(
            @ResourceId UUID id, String schema, String table, int offset, int limit) {
        return browseService.browse(id, schema, table, offset, limit, currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.DB_QUERY_READ)
    public QueryResult query(@ResourceId UUID id, QueryRequest request) {
        return browseService.query(id, request.sql(), QueryMode.READ, currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.DB_QUERY_WRITE)
    public QueryResult execute(@ResourceId UUID id, QueryRequest request) {
        return browseService.query(id, request.sql(), QueryMode.WRITE, currentUser.require());
    }
}
