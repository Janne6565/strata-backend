package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.action.QueryRequest;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Browse/query contract for a datasource. Authorization is enforced on the implementation. */
@Tag(name = "Browse")
@RequestMapping(path = "/v1/datasources/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public interface BrowseApi {

    @Operation(summary = "Introspect the datasource schema (tables/columns)")
    @GetMapping("/schema")
    SchemaInfo schema(@PathVariable UUID id);

    @Operation(
            summary = "Browse a page of rows from a table",
            description =
                    "Optional server-side ordering (orderBy + direction ASC/DESC) and column"
                            + " filters. Each filter is 'column:op:value' where op is one of"
                            + " eq, ne, lt, lte, gt, gte, like, isnull, isnotnull (the last two take no"
                            + " value). Sort/filter apply on relational engines; others ignore them.")
    @GetMapping("/tables/{schema}/{table}/rows")
    RowPage browse(
            @PathVariable UUID id,
            @PathVariable String schema,
            @PathVariable String table,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String direction,
            @RequestParam(name = "filter", required = false) List<String> filter);

    // Explicit operationId: the default ("query") would make the generated client emit a
    // `QueryResult` result-type alias that collides with the QueryResult model import.
    @Operation(operationId = "runQuery", summary = "Run a read-only query")
    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    QueryResult query(@PathVariable UUID id, @Valid @RequestBody QueryRequest request);

    @Operation(operationId = "runExecute", summary = "Execute a writing query")
    @PostMapping(path = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    QueryResult execute(@PathVariable UUID id, @Valid @RequestBody QueryRequest request);
}
