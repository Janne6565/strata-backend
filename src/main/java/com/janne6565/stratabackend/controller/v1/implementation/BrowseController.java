package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.BrowseApi;
import com.janne6565.stratabackend.model.action.QueryRequest;
import com.janne6565.stratabackend.model.core.BrowseQuery;
import com.janne6565.stratabackend.model.core.ColumnFilter;
import com.janne6565.stratabackend.model.core.FilterOp;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.SortDirection;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.security.authorization.ResourceId;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.engine.BrowseService;
import com.janne6565.stratabackend.services.engine.StoredObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Implements {@link BrowseApi}. Read operations are gated by DB_BROWSE/DB_QUERY_READ; the separate
 * /execute endpoint is gated by DB_QUERY_WRITE so write authorization (writable grant + prod
 * safe-mode) is decided by the policy aspect, not by parsing SQL.
 */
@RestController
@RequiredArgsConstructor
public class BrowseController implements BrowseApi {

    private final BrowseService browseService;
    private final CurrentUser currentUser;

    @Override
    @NeedsValidation(Operation.DB_BROWSE)
    public SchemaInfo schema(@ResourceId UUID id) {
        return browseService.schema(id);
    }

    @Override
    @NeedsValidation(Operation.DB_BROWSE)
    public RowPage browse(
            @ResourceId UUID id,
            String schema,
            String table,
            int offset,
            int limit,
            String orderBy,
            String direction,
            List<String> filter) {
        BrowseQuery query =
                new BrowseQuery(
                        offset,
                        limit,
                        blankToNull(orderBy),
                        SortDirection.fromString(direction),
                        parseFilters(filter));
        return browseService.browse(id, schema, table, query, currentUser.require());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Parses the {@code column:op:value} filter params. Splits into at most three parts so a value
     * may itself contain colons; the operator token is mapped to a fixed {@link FilterOp}.
     */
    private static List<ColumnFilter> parseFilters(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ColumnFilter> filters = new ArrayList<>(raw.size());
        for (String entry : raw) {
            String[] parts = entry.split(":", 3);
            if (parts.length < 2 || parts[0].isBlank()) {
                throw new BadRequestException("Malformed filter: " + entry);
            }
            FilterOp op = FilterOp.fromToken(parts[1]);
            String value = parts.length == 3 ? parts[2] : null;
            if (op.needsValue() && value == null) {
                throw new BadRequestException("Filter requires a value: " + entry);
            }
            filters.add(new ColumnFilter(parts[0], op, value));
        }
        return filters;
    }

    @Override
    @NeedsValidation(Operation.DB_BROWSE)
    public ResponseEntity<StreamingResponseBody> objectContent(
            @ResourceId UUID id, String bucket, String key) {
        StoredObject object = browseService.openObject(id, bucket, key, currentUser.require());
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType(object.contentType()));
        if (object.sizeBytes() >= 0) {
            headers.setContentLength(object.sizeBytes());
        }
        // The bytes are uploader-controlled: never let a browser render them on our origin.
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "sandbox; default-src 'none'");
        StreamingResponseBody body =
                out -> {
                    try (object) {
                        object.content().transferTo(out);
                    }
                };
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static MediaType contentType(String stored) {
        try {
            return stored == null || stored.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(stored);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
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
