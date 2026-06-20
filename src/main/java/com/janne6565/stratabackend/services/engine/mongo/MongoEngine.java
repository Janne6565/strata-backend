package com.janne6565.stratabackend.services.engine.mongo;

import com.janne6565.stratabackend.model.core.ColumnInfo;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.TableInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.janne6565.stratabackend.services.engine.DatabaseEngine;
import com.janne6565.stratabackend.services.engine.EngineMetrics;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PreDestroy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * MongoDB adapter (ARCHITECTURE.md §9). Collections map onto the table abstraction: introspection
 * lists collections and infers columns from a sample document; browse pages a collection; queries
 * are database commands ({@code db.runCommand}) supplied as JSON. In {@link QueryMode#READ} only a
 * whitelist of read commands is permitted and aggregation write stages ({@code $out}/{@code
 * $merge}) are rejected — the engine's read-only enforcement (AUTH.md, defence-in-depth). Clients
 * are cached per endpoint; resolved credentials live only in the connection string, in memory.
 */
@Component
public class MongoEngine implements DatabaseEngine {

    private static final int MAX_QUERY_ROWS = 1000;

    /** Commands permitted in read-only mode (lower-cased command name). */
    private static final Set<String> READ_COMMANDS =
            Set.of(
                    "find",
                    "aggregate",
                    "count",
                    "distinct",
                    "listcollections",
                    "listindexes",
                    "dbstats",
                    "collstats",
                    "explain",
                    "geosearch");

    private final Map<String, MongoClient> clients = new ConcurrentHashMap<>();

    @Override
    public String driver() {
        return "mongodb";
    }

    @Override
    public boolean canEnforceReadOnly() {
        return true;
    }

    @Override
    public SchemaInfo introspect(ConnectionDetails details) {
        try {
            MongoDatabase db = database(details);
            List<TableInfo> tables = new ArrayList<>();
            for (String name : db.listCollectionNames()) {
                // estimatedDocumentCount() reads collection metadata — cheap, approximate.
                long count = db.getCollection(name).estimatedDocumentCount();
                tables.add(
                        new TableInfo(
                                db.getName(), name, "COLLECTION", inferColumns(db, name), count));
            }
            return new SchemaInfo(tables);
        } catch (RuntimeException ex) {
            throw new EngineException("Introspection failed: " + ex.getMessage());
        }
    }

    @Override
    public RowPage browse(ConnectionDetails details, ObjectRef ref, int offset, int limit) {
        try {
            List<Document> docs = new ArrayList<>();
            database(details)
                    .getCollection(ref.name())
                    .find()
                    .skip(Math.max(0, offset))
                    .limit(Math.max(0, limit))
                    .into(docs);
            List<String> columns = unionKeys(docs);
            return new RowPage(columns, toRows(docs, columns), offset, limit);
        } catch (RuntimeException ex) {
            throw new EngineException("Browse failed: " + ex.getMessage());
        }
    }

    @Override
    public QueryResult runQuery(ConnectionDetails details, String query, QueryMode mode) {
        Document command;
        try {
            command = Document.parse(query);
        } catch (RuntimeException ex) {
            throw new EngineException("Invalid command JSON: " + ex.getMessage());
        }
        if (command.isEmpty()) {
            throw new EngineException("Empty command");
        }
        String name = command.keySet().iterator().next();
        if (mode == QueryMode.READ) {
            guardReadOnly(name, command);
        }
        try {
            return toResult(database(details).runCommand(command));
        } catch (RuntimeException ex) {
            throw new EngineException("Query failed: " + ex.getMessage());
        }
    }

    @Override
    public Optional<EngineMetrics> sampleMetrics(ConnectionDetails details) {
        try {
            MongoDatabase db = database(details);
            Document stats = db.runCommand(new Document("dbStats", 1));
            Long dataSize = asLong(stats.get("dataSize"));
            Integer collections = asInt(stats.get("collections"));
            // connections live in serverStatus, which may be denied for non-admin users.
            Integer connections = null;
            try {
                Document server = db.runCommand(new Document("serverStatus", 1));
                if (server.get("connections") instanceof Document conn) {
                    connections = asInt(conn.get("current"));
                }
            } catch (RuntimeException ignored) {
                // best-effort: leave connections null when serverStatus isn't permitted
            }
            return Optional.of(new EngineMetrics(connections, dataSize, collections));
        } catch (RuntimeException ex) {
            throw new EngineException("Metrics failed: " + ex.getMessage());
        }
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private void guardReadOnly(String name, Document command) {
        if (!READ_COMMANDS.contains(name.toLowerCase())) {
            throw new EngineException("Command '" + name + "' is not permitted in read-only mode");
        }
        if (name.equalsIgnoreCase("aggregate")) {
            List<?> pipeline = command.getList("pipeline", Object.class, List.of());
            for (Object stage : pipeline) {
                if (stage instanceof Document d
                        && (d.containsKey("$out") || d.containsKey("$merge"))) {
                    throw new EngineException(
                            "Aggregation write stages are not permitted in read-only mode");
                }
            }
        }
    }

    private List<ColumnInfo> inferColumns(MongoDatabase db, String collection) {
        Document sample = db.getCollection(collection).find().first();
        List<ColumnInfo> columns = new ArrayList<>();
        if (sample != null) {
            for (String field : sample.keySet()) {
                Object value = sample.get(field);
                String type = value == null ? "null" : value.getClass().getSimpleName();
                columns.add(new ColumnInfo(field, type, true, field.equals("_id")));
            }
        }
        return columns;
    }

    private QueryResult toResult(Document result) {
        if (result.get("cursor") instanceof Document cursor
                && cursor.get("firstBatch") instanceof List<?> batch) {
            List<Document> docs = new ArrayList<>();
            for (Object element : batch) {
                if (element instanceof Document doc) {
                    docs.add(doc);
                }
            }
            List<String> columns = unionKeys(docs);
            return QueryResult.ofRows(columns, toRows(docs, columns));
        }
        List<String> columns = new ArrayList<>(result.keySet());
        return QueryResult.ofRows(columns, List.of(toRow(result, columns)));
    }

    private List<String> unionKeys(List<Document> docs) {
        Set<String> keys = new LinkedHashSet<>();
        for (Document doc : docs) {
            keys.addAll(doc.keySet());
        }
        return new ArrayList<>(keys);
    }

    private List<List<Object>> toRows(List<Document> docs, List<String> columns) {
        List<List<Object>> rows = new ArrayList<>();
        for (Document doc : docs) {
            if (rows.size() >= MAX_QUERY_ROWS) {
                break;
            }
            rows.add(toRow(doc, columns));
        }
        return rows;
    }

    private List<Object> toRow(Document doc, List<String> columns) {
        List<Object> row = new ArrayList<>(columns.size());
        for (String column : columns) {
            row.add(cell(doc.get(column)));
        }
        return row;
    }

    /** Coerces a BSON value into a JSON-serialisable cell. */
    private Object cell(Object value) {
        if (value == null
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof String) {
            return value;
        }
        if (value instanceof Document document) {
            return document.toJson();
        }
        return value.toString();
    }

    private MongoDatabase database(ConnectionDetails details) {
        MongoClient client =
                clients.computeIfAbsent(key(details), k -> MongoClients.create(uri(details)));
        return client.getDatabase(details.database());
    }

    private String key(ConnectionDetails details) {
        return details.host() + ":" + details.port() + ":" + details.username();
    }

    private String uri(ConnectionDetails details) {
        boolean authenticated = details.username() != null && !details.username().isBlank();
        String credentials =
                authenticated
                        ? encode(details.username()) + ":" + encode(details.password()) + "@"
                        : "";
        String options = authenticated ? "/?authSource=admin" : "";
        return "mongodb://" + credentials + details.host() + ":" + details.port() + options;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @PreDestroy
    void closeAll() {
        clients.values().forEach(MongoClient::close);
        clients.clear();
    }
}
