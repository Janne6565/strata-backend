package com.janne6565.stratabackend.services.engine.loki;

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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grafana Loki adapter (ARCHITECTURE.md §9). Loki is a log store queried over HTTP with LogQL; it
 * has no JDBC driver, so this adapter speaks the HTTP API directly. Introspect exposes the log
 * stream and its label names; browse pages recent log lines; runQuery executes a LogQL query. Loki
 * is read-only through its query API, so {@link QueryMode#WRITE} is rejected outright (AUTH.md,
 * defence-in-depth) — ingestion is out of scope for this browser.
 */
@Component
public class LokiEngine implements DatabaseEngine {

    private static final int MAX_QUERY_ROWS = 1000;
    private static final long HOUR_NS = 3_600_000_000_000L;
    private static final long MINUTE_NS = 60_000_000_000L;
    private static final String LOGS_OBJECT = "logs";

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Override
    public String driver() {
        return "loki";
    }

    @Override
    public boolean canEnforceReadOnly() {
        return true;
    }

    @Override
    public SchemaInfo introspect(ConnectionDetails details) {
        try {
            List<ColumnInfo> columns = new ArrayList<>();
            columns.add(new ColumnInfo("timestamp", "string", false, false));
            columns.add(new ColumnInfo("line", "string", false, false));
            for (String label : labelNames(details)) {
                columns.add(new ColumnInfo(label, "label", true, false));
            }
            return new SchemaInfo(
                    List.of(new TableInfo("loki", LOGS_OBJECT, "STREAM", columns, null)));
        } catch (RuntimeException ex) {
            throw new EngineException("Introspection failed: " + ex.getMessage());
        }
    }

    @Override
    public RowPage browse(ConnectionDetails details, ObjectRef ref, int offset, int limit) {
        try {
            List<String> labels = labelNames(details);
            if (labels.isEmpty()) {
                throw new EngineException("Loki has no labels to browse");
            }
            String selector = "{" + labels.get(0) + "=~\".+\"}";
            List<Entry> entries =
                    queryRange(details, selector, Math.max(0, offset) + Math.max(0, limit));
            int from = Math.min(Math.max(0, offset), entries.size());
            int to = Math.min(from + Math.max(0, limit), entries.size());
            return toPage(entries.subList(from, to), offset, limit);
        } catch (EngineException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new EngineException("Browse failed: " + ex.getMessage());
        }
    }

    @Override
    public QueryResult runQuery(ConnectionDetails details, String query, QueryMode mode) {
        if (mode == QueryMode.WRITE) {
            throw new EngineException("Loki is read-only; writes are not supported");
        }
        try {
            List<Entry> entries = queryRange(details, query, MAX_QUERY_ROWS);
            RowPage page = toPage(entries, 0, entries.size());
            return QueryResult.ofRows(page.columns(), page.rows());
        } catch (EngineException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new EngineException("Query failed: " + ex.getMessage());
        }
    }

    private RowPage toPage(List<Entry> entries, int offset, int limit) {
        Set<String> labelKeys = new LinkedHashSet<>();
        for (Entry entry : entries) {
            labelKeys.addAll(entry.labels().keySet());
        }
        List<String> columns = new ArrayList<>();
        columns.add("timestamp");
        columns.add("line");
        columns.addAll(labelKeys);
        List<List<Object>> rows = new ArrayList<>();
        for (Entry entry : entries) {
            List<Object> row = new ArrayList<>(columns.size());
            row.add(entry.timestamp());
            row.add(entry.line());
            for (String key : labelKeys) {
                row.add(entry.labels().get(key));
            }
            rows.add(row);
        }
        return new RowPage(columns, rows, offset, limit);
    }

    private List<String> labelNames(ConnectionDetails details) {
        long now = nowNs();
        String url =
                baseUrl(details)
                        + "/loki/api/v1/labels?start="
                        + (now - HOUR_NS)
                        + "&end="
                        + (now + MINUTE_NS);
        JsonNode data = get(details, url).path("data");
        List<String> labels = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode label : data) {
                labels.add(label.asString());
            }
        }
        return labels;
    }

    private List<Entry> queryRange(ConnectionDetails details, String logql, int limit) {
        long now = nowNs();
        String url =
                baseUrl(details)
                        + "/loki/api/v1/query_range?query="
                        + encode(logql)
                        + "&start="
                        + (now - HOUR_NS)
                        + "&end="
                        + (now + MINUTE_NS)
                        + "&limit="
                        + Math.max(1, limit)
                        + "&direction=backward";
        JsonNode result = get(details, url).path("data").path("result");
        List<Entry> entries = new ArrayList<>();
        for (JsonNode stream : result) {
            Map<String, String> labels = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> property : stream.path("stream").properties()) {
                labels.put(property.getKey(), property.getValue().asString());
            }
            for (JsonNode pair : stream.path("values")) {
                if (entries.size() >= MAX_QUERY_ROWS) {
                    break;
                }
                entries.add(new Entry(labels, pair.get(0).asString(), pair.get(1).asString()));
            }
        }
        return entries;
    }

    private JsonNode get(ConnectionDetails details, String url) {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(15));
        if (details.username() != null && !details.username().isBlank()) {
            String token =
                    details.username()
                            + ":"
                            + (details.password() == null ? "" : details.password());
            request.header(
                    "Authorization",
                    "Basic "
                            + Base64.getEncoder()
                                    .encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new EngineException("Loki returned HTTP " + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (java.io.IOException ex) {
            throw new EngineException("Loki request failed: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EngineException("Loki request interrupted");
        }
    }

    private String baseUrl(ConnectionDetails details) {
        return "http://" + details.host() + ":" + details.port();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private long nowNs() {
        return System.currentTimeMillis() * 1_000_000L;
    }

    /** A single log entry: its stream labels, nanosecond timestamp and the log line. */
    private record Entry(Map<String, String> labels, String timestamp, String line) {}
}
