package com.janne6565.stratabackend.services.engine.minio;

import com.janne6565.stratabackend.model.core.BrowseQuery;
import com.janne6565.stratabackend.model.core.ColumnFilter;
import com.janne6565.stratabackend.model.core.ColumnInfo;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.FilterOp;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.SortDirection;
import com.janne6565.stratabackend.model.core.TableInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.janne6565.stratabackend.services.engine.DatabaseEngine;
import com.janne6565.stratabackend.services.engine.EngineMetrics;
import com.janne6565.stratabackend.services.engine.minio.S3Client.Bucket;
import com.janne6565.stratabackend.services.engine.minio.S3Client.Listing;
import com.janne6565.stratabackend.services.engine.minio.S3Client.S3Object;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * MinIO (S3-compatible object storage) adapter (ARCHITECTURE.md §9), speaking the S3 HTTP API
 * directly with SigV4 ({@link S3Client}). Buckets map to tables: introspect lists them with their
 * object count and total size; browse lists one folder level of a bucket (folders first) — the
 * folder is chosen with a {@code prefix} filter ({@code prefix:eq:photos/2024/}); runQuery takes a
 * small read-only command language ({@code ls}, {@code du}, {@code stat}). Object bodies are never
 * read and nothing is written, so {@link QueryMode#WRITE} is rejected outright.
 *
 * <p>Sizes come from MinIO's admin data-usage endpoint when the credentials allow it (root user —
 * what discovery resolves), else from a capped recursive listing that is cached briefly so metric
 * polling doesn't re-list large buckets on every tick.
 */
@Component
public class MinioEngine implements DatabaseEngine {

    /** The browse filter column that selects the folder (key prefix) to list. */
    public static final String PREFIX_FILTER = "prefix";

    static final String SCHEMA = "minio";
    static final String FOLDER = "folder";
    static final String OBJECT = "object";

    private static final List<String> COLUMN_NAMES =
            List.of("name", "key", "type", "size", "last_modified", "etag", "storage_class");
    private static final List<ColumnInfo> COLUMNS =
            List.of(
                    new ColumnInfo("name", "string", false, false),
                    new ColumnInfo("key", "string", false, true),
                    new ColumnInfo("type", "string", false, false),
                    new ColumnInfo("size", "bytes", true, false),
                    new ColumnInfo("last_modified", "timestamp", true, false),
                    new ColumnInfo("etag", "string", true, false),
                    new ColumnInfo("storage_class", "string", true, false));

    /** Max entries read for one folder level (browse sorts/pages within this window). */
    private static final int BROWSE_SCAN_CAP = 10_000;

    /** Max objects read per bucket when sizing without the admin API. */
    private static final int USAGE_SCAN_CAP = 50_000;

    private static final int MAX_QUERY_ROWS = 1000;
    private static final Duration USAGE_CACHE_TTL = Duration.ofMinutes(2);
    private static final String ZERO_TIME_PREFIX = "0001-01-01";
    private static final String USAGE_HINT =
            "Supported commands: ls [bucket[/prefix]], du [bucket[/prefix]], stat bucket/key";

    private final HttpClient http =
            HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<String, CachedUsage> usageCache = new ConcurrentHashMap<>();

    /** A bucket's (or prefix's) size; {@code exact} is false when a listing hit its cap. */
    record Usage(long sizeBytes, long objects, boolean exact) {}

    private record CachedUsage(Usage usage, long expiresAtMillis) {}

    @Override
    public String driver() {
        return "minio";
    }

    @Override
    public boolean canEnforceReadOnly() {
        return true;
    }

    @Override
    public SchemaInfo introspect(ConnectionDetails details) {
        try {
            S3Client client = client(details);
            List<Bucket> buckets = client.listBuckets();
            Map<String, Usage> usage = bucketUsage(client, details, buckets);
            List<TableInfo> tables = new ArrayList<>();
            for (Bucket bucket : buckets) {
                Usage u = usage.get(bucket.name());
                tables.add(
                        new TableInfo(
                                SCHEMA,
                                bucket.name(),
                                "BUCKET",
                                COLUMNS,
                                u == null ? null : u.objects(),
                                u == null ? null : u.sizeBytes()));
            }
            return new SchemaInfo(tables);
        } catch (RuntimeException ex) {
            throw new EngineException("Introspection failed: " + ex.getMessage());
        }
    }

    @Override
    public RowPage browse(ConnectionDetails details, ObjectRef ref, int offset, int limit) {
        return browse(details, ref, BrowseQuery.paged(offset, limit));
    }

    @Override
    public RowPage browse(ConnectionDetails details, ObjectRef ref, BrowseQuery query) {
        try {
            String prefix = prefixFilter(query.filters());
            List<List<Object>> rows = folderRows(client(details), ref.name(), prefix);
            sort(rows, query);
            int from = Math.min(Math.max(0, query.offset()), rows.size());
            int to = Math.min(from + Math.max(0, query.limit()), rows.size());
            return new RowPage(
                    COLUMN_NAMES,
                    new ArrayList<>(rows.subList(from, to)),
                    query.offset(),
                    query.limit());
        } catch (RuntimeException ex) {
            throw new EngineException("Browse failed: " + ex.getMessage());
        }
    }

    @Override
    public QueryResult runQuery(ConnectionDetails details, String query, QueryMode mode) {
        if (mode == QueryMode.WRITE) {
            throw new EngineException("MinIO is browsed read-only; writes are not supported");
        }
        String[] tokens = query == null ? new String[0] : query.trim().split("\\s+", 2);
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            throw new EngineException("Empty command. " + USAGE_HINT);
        }
        String argument = tokens.length > 1 ? normalizePath(tokens[1]) : "";
        try {
            S3Client client = client(details);
            return switch (tokens[0].toLowerCase()) {
                case "ls" ->
                        argument.isEmpty() ? listBuckets(client, details) : ls(client, argument);
                case "du" -> du(client, details, argument);
                case "stat" -> stat(client, argument);
                default ->
                        throw new EngineException(
                                "Unknown command '" + tokens[0] + "'. " + USAGE_HINT);
            };
        } catch (RuntimeException ex) {
            throw new EngineException("Query failed: " + ex.getMessage());
        }
    }

    @Override
    public Optional<EngineMetrics> sampleMetrics(ConnectionDetails details) {
        try {
            S3Client client = client(details);
            List<Bucket> buckets = client.listBuckets();
            Map<String, Usage> usage = bucketUsage(client, details, buckets);
            long size = 0;
            long objects = 0;
            for (Usage u : usage.values()) {
                size += u.sizeBytes();
                objects += u.objects();
            }
            return Optional.of(
                    new EngineMetrics(null, size, (int) Math.min(objects, Integer.MAX_VALUE)));
        } catch (RuntimeException ex) {
            throw new EngineException("Metrics failed: " + ex.getMessage());
        }
    }

    // --- browse ------------------------------------------------------------------------------

    private List<List<Object>> folderRows(S3Client client, String bucket, String prefix) {
        Listing listing = client.list(bucket, prefix, "/", BROWSE_SCAN_CAP);
        List<List<Object>> rows = new ArrayList<>();
        for (String folder : listing.prefixes()) {
            rows.add(row(displayName(folder, prefix), folder, FOLDER, null, null, null, null));
        }
        for (S3Object object : listing.objects()) {
            if (object.key().equals(prefix)) {
                // The zero-byte "folder marker" object the MinIO console creates for a folder.
                continue;
            }
            rows.add(
                    row(
                            displayName(object.key(), prefix),
                            object.key(),
                            OBJECT,
                            object.size(),
                            object.lastModified(),
                            object.etag(),
                            object.storageClass()));
        }
        return rows;
    }

    private static List<Object> row(
            String name,
            String key,
            String type,
            Long size,
            String lastModified,
            String etag,
            String storageClass) {
        return new ArrayList<>(
                Arrays.asList(name, key, type, size, lastModified, etag, storageClass));
    }

    /** Folders always lead; within each group, order by the requested column (default: name). */
    private static void sort(List<List<Object>> rows, BrowseQuery query) {
        int index = query.hasOrder() ? COLUMN_NAMES.indexOf(query.orderBy()) : -1;
        if (query.hasOrder() && index < 0) {
            throw new EngineException("Unknown column: " + query.orderBy());
        }
        int column = index < 0 ? 0 : index;
        Comparator<List<Object>> byValue =
                Comparator.comparing(
                        (List<Object> r) -> (Comparable<Object>) comparable(r.get(column)),
                        Comparator.nullsLast(Comparator.naturalOrder()));
        if (query.direction() == SortDirection.DESC) {
            byValue = byValue.reversed();
        }
        Comparator<List<Object>> foldersFirst =
                Comparator.comparing((List<Object> r) -> FOLDER.equals(r.get(2)) ? 0 : 1);
        rows.sort(foldersFirst.thenComparing(byValue));
    }

    @SuppressWarnings("unchecked")
    private static Comparable<Object> comparable(Object value) {
        return (Comparable<Object>) value;
    }

    private static String prefixFilter(List<ColumnFilter> filters) {
        for (ColumnFilter filter : filters) {
            if (!PREFIX_FILTER.equals(filter.column())) {
                throw new EngineException(
                        "MinIO browse only supports the '" + PREFIX_FILTER + "' filter");
            }
            if (filter.op() != FilterOp.EQ) {
                throw new EngineException("The '" + PREFIX_FILTER + "' filter only supports eq");
            }
            String value = filter.value() == null ? "" : filter.value();
            return value.startsWith("/") ? value.substring(1) : value;
        }
        return "";
    }

    /** The last path segment relative to the listed prefix (folders keep their trailing /). */
    private static String displayName(String key, String prefix) {
        String relative = key.startsWith(prefix) ? key.substring(prefix.length()) : key;
        return relative.isEmpty() ? key : relative;
    }

    // --- query commands ----------------------------------------------------------------------

    private QueryResult listBuckets(S3Client client, ConnectionDetails details) {
        List<Bucket> buckets = client.listBuckets();
        Map<String, Usage> usage = bucketUsage(client, details, buckets);
        List<List<Object>> rows = new ArrayList<>();
        for (Bucket bucket : buckets) {
            Usage u = usage.get(bucket.name());
            rows.add(
                    Arrays.asList(
                            bucket.name(),
                            u == null ? null : u.objects(),
                            u == null ? null : u.sizeBytes(),
                            bucket.creationDate()));
        }
        return QueryResult.ofRows(List.of("bucket", "objects", "size", "created"), rows);
    }

    private QueryResult ls(S3Client client, String path) {
        String[] parts = splitPath(path);
        List<List<Object>> rows = folderRows(client, parts[0], parts[1]);
        sort(rows, BrowseQuery.paged(0, MAX_QUERY_ROWS));
        return QueryResult.ofRows(
                COLUMN_NAMES,
                new ArrayList<>(rows.subList(0, Math.min(rows.size(), MAX_QUERY_ROWS))));
    }

    private QueryResult du(S3Client client, ConnectionDetails details, String path) {
        List<String> columns = List.of("path", "objects", "size", "exact");
        if (path.isEmpty()) {
            List<Bucket> buckets = client.listBuckets();
            Map<String, Usage> usage = bucketUsage(client, details, buckets);
            List<List<Object>> rows = new ArrayList<>();
            long size = 0;
            long objects = 0;
            boolean exact = true;
            for (Bucket bucket : buckets) {
                Usage u = usage.get(bucket.name());
                rows.add(List.of(bucket.name(), u.objects(), u.sizeBytes(), u.exact()));
                size += u.sizeBytes();
                objects += u.objects();
                exact &= u.exact();
            }
            rows.add(List.of("(total)", objects, size, exact));
            return QueryResult.ofRows(columns, rows);
        }
        String[] parts = splitPath(path);
        Usage usage = scanUsage(client, parts[0], parts[1]);
        return QueryResult.ofRows(
                columns, List.of(List.of(path, usage.objects(), usage.sizeBytes(), usage.exact())));
    }

    private QueryResult stat(S3Client client, String path) {
        String[] parts = splitPath(path);
        if (parts[1].isEmpty()) {
            throw new EngineException("stat needs an object path: stat bucket/key");
        }
        Map<String, List<String>> headers = client.headObject(parts[0], parts[1]);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("key", parts[1]));
        headers.forEach(
                (name, values) -> {
                    String lower = name.toLowerCase();
                    if (lower.startsWith("content-")
                            || lower.equals("etag")
                            || lower.equals("last-modified")
                            || lower.startsWith("x-amz-")) {
                        rows.add(List.of(lower, String.join(", ", values)));
                    }
                });
        return QueryResult.ofRows(List.of("field", "value"), rows);
    }

    /** {@code bucket/some/prefix} → {@code [bucket, some/prefix]}. */
    private static String[] splitPath(String path) {
        int slash = path.indexOf('/');
        return slash < 0
                ? new String[] {path, ""}
                : new String[] {path.substring(0, slash), path.substring(slash + 1)};
    }

    private static String normalizePath(String raw) {
        String path = raw.trim();
        if (path.startsWith("s3://")) {
            path = path.substring("s3://".length());
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    // --- usage -------------------------------------------------------------------------------

    /**
     * Per-bucket usage: the admin data-usage summary where available, a capped (cached) listing for
     * any bucket it doesn't cover.
     */
    Map<String, Usage> bucketUsage(
            S3Client client, ConnectionDetails details, List<Bucket> buckets) {
        Map<String, Usage> fromAdmin = adminUsage(client);
        Map<String, Usage> usage = new LinkedHashMap<>();
        for (Bucket bucket : buckets) {
            Usage u = fromAdmin.get(bucket.name());
            usage.put(bucket.name(), u != null ? u : cachedScan(client, details, bucket.name()));
        }
        return usage;
    }

    private Map<String, Usage> adminUsage(S3Client client) {
        Optional<JsonNode> info = client.dataUsage();
        if (info.isEmpty()) {
            return Map.of();
        }
        return parseDataUsage(info.get());
    }

    /**
     * Reads {@code bucketsUsageInfo} from MinIO's admin data-usage JSON (ignored before 1st scan).
     */
    static Map<String, Usage> parseDataUsage(JsonNode info) {
        String lastUpdate = info.path("lastUpdate").asString("");
        JsonNode perBucket = info.path("bucketsUsageInfo");
        if (lastUpdate.isEmpty()
                || lastUpdate.startsWith(ZERO_TIME_PREFIX)
                || !perBucket.isObject()) {
            return Map.of();
        }
        Map<String, Usage> usage = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : perBucket.properties()) {
            JsonNode bucket = entry.getValue();
            usage.put(
                    entry.getKey(),
                    new Usage(
                            bucket.path("size").asLong(0),
                            bucket.path("objectsCount").asLong(0),
                            true));
        }
        return usage;
    }

    private Usage cachedScan(S3Client client, ConnectionDetails details, String bucket) {
        String key = details.host() + ":" + details.port() + "/" + bucket;
        long now = System.currentTimeMillis();
        CachedUsage cached = usageCache.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.usage();
        }
        Usage usage = scanUsage(client, bucket, "");
        usageCache.put(key, new CachedUsage(usage, now + USAGE_CACHE_TTL.toMillis()));
        return usage;
    }

    private static Usage scanUsage(S3Client client, String bucket, String prefix) {
        Listing listing = client.list(bucket, prefix, null, USAGE_SCAN_CAP);
        long size = 0;
        for (S3Object object : listing.objects()) {
            size += object.size();
        }
        return new Usage(size, listing.objects().size(), !listing.truncated());
    }

    private S3Client client(ConnectionDetails details) {
        return new S3Client(http, mapper, details);
    }
}
