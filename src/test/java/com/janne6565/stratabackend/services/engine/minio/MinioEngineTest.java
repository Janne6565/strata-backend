package com.janne6565.stratabackend.services.engine.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.model.core.BrowseQuery;
import com.janne6565.stratabackend.model.core.ColumnFilter;
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
import com.janne6565.stratabackend.services.engine.EngineMetrics;
import com.janne6565.stratabackend.services.engine.StoredObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the MinIO adapter against an in-process fake of the S3 endpoints it calls, so the
 * listing/paging/usage logic is covered without Docker ({@link MinioEngineIntegrationTest} runs the
 * same adapter against a real MinIO).
 */
class MinioEngineTest {

    /** Bucket {@code photos}: two folders, a folder marker, and three objects at the root. */
    private static final Map<String, Long> PHOTOS =
            Map.of(
                    "2024/", 0L,
                    "2024/a.jpg", 1_000L,
                    "2024/b.jpg", 2_000L,
                    "raw/c.cr2", 30_000L,
                    "readme.txt", 10L,
                    "zebra.png", 500L,
                    "big.bin", 7_000L);

    private HttpServer server;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private volatile boolean adminAvailable;
    private final MinioEngine engine = new MinioEngine();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ConnectionDetails details() {
        return new ConnectionDetails(
                "minio", "127.0.0.1", server.getAddress().getPort(), "root", "root", "secret");
    }

    @Test
    void introspectListsBucketsWithCountAndSizeFromListing() {
        SchemaInfo schema = engine.introspect(details());

        assertThat(schema.tables()).extracting(TableInfo::name).containsExactly("empty", "photos");
        TableInfo photos = schema.tables().get(1);
        assertThat(photos.schema()).isEqualTo("minio");
        assertThat(photos.type()).isEqualTo("BUCKET");
        assertThat(photos.rowCount()).isEqualTo(7L);
        assertThat(photos.sizeBytes()).isEqualTo(40_510L);
        assertThat(schema.tables().get(0).sizeBytes()).isZero();
        assertThat(authorizations).allMatch(a -> a.startsWith("AWS4-HMAC-SHA256 Credential=root/"));
    }

    @Test
    void introspectPrefersAdminDataUsage() {
        adminAvailable = true;

        TableInfo photos = engine.introspect(details()).tables().get(1);

        assertThat(photos.rowCount()).isEqualTo(99L);
        assertThat(photos.sizeBytes()).isEqualTo(123_456L);
    }

    @Test
    void browseListsOneLevelFoldersFirst() {
        RowPage page = engine.browse(details(), new ObjectRef("minio", "photos"), 0, 50);

        assertThat(page.columns())
                .containsExactly(
                        "name", "key", "type", "size", "last_modified", "etag", "storage_class");
        assertThat(page.rows())
                .extracting(r -> r.get(0))
                .containsExactly("2024/", "raw/", "big.bin", "readme.txt", "zebra.png");
        assertThat(page.rows().get(0).get(2)).isEqualTo("folder");
        assertThat(page.rows().get(0).get(3)).isNull();
        assertThat(page.rows().get(2)).containsSequence("big.bin", "big.bin", "object", 7_000L);
    }

    @Test
    void browseIntoFolderViaPrefixFilterSkipsFolderMarker() {
        BrowseQuery query =
                new BrowseQuery(
                        0,
                        50,
                        null,
                        null,
                        List.of(new ColumnFilter("prefix", FilterOp.EQ, "2024/")));

        RowPage page = engine.browse(details(), new ObjectRef("minio", "photos"), query);

        assertThat(page.rows()).extracting(r -> r.get(0)).containsExactly("a.jpg", "b.jpg");
        assertThat(page.rows())
                .extracting(r -> r.get(1))
                .containsExactly("2024/a.jpg", "2024/b.jpg");
    }

    @Test
    void browseSortsBySizeWithinGroupsAndPages() {
        BrowseQuery query = new BrowseQuery(0, 3, "size", SortDirection.DESC, List.of());

        RowPage first = engine.browse(details(), new ObjectRef("minio", "photos"), query);
        RowPage second =
                engine.browse(
                        details(),
                        new ObjectRef("minio", "photos"),
                        new BrowseQuery(3, 3, "size", SortDirection.DESC, List.of()));

        assertThat(first.rows())
                .extracting(r -> r.get(0))
                .containsExactly("2024/", "raw/", "big.bin");
        assertThat(second.rows())
                .extracting(r -> r.get(0))
                .containsExactly("zebra.png", "readme.txt");
    }

    @Test
    void browseRejectsUnsupportedFilters() {
        BrowseQuery query =
                new BrowseQuery(
                        0, 50, null, null, List.of(new ColumnFilter("size", FilterOp.GT, "5")));

        assertThatThrownBy(() -> engine.browse(details(), new ObjectRef("minio", "photos"), query))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("prefix");
    }

    @Test
    void metricsSumAllBuckets() {
        EngineMetrics metrics = engine.sampleMetrics(details()).orElseThrow();

        assertThat(metrics.dataSizeBytes()).isEqualTo(40_510L);
        assertThat(metrics.objectCount()).isEqualTo(7);
        assertThat(metrics.connections()).isNull();
    }

    @Test
    void queryCommands() {
        QueryResult ls = engine.runQuery(details(), "ls", QueryMode.READ);
        assertThat(ls.columns()).containsExactly("bucket", "objects", "size", "created");
        assertThat(ls.rows()).hasSize(2);

        QueryResult folder = engine.runQuery(details(), "ls s3://photos/2024/", QueryMode.READ);
        assertThat(folder.rows()).extracting(r -> r.get(0)).containsExactly("a.jpg", "b.jpg");

        QueryResult du = engine.runQuery(details(), "du photos/2024/", QueryMode.READ);
        assertThat(du.rows()).containsExactly(List.of("photos/2024/", 3L, 3_000L, true));

        QueryResult total = engine.runQuery(details(), "du", QueryMode.READ);
        assertThat(total.rows().getLast()).containsExactly("(total)", 7L, 40_510L, true);
    }

    @Test
    void rejectsWritesAndUnknownCommands() {
        assertThatThrownBy(() -> engine.runQuery(details(), "ls", QueryMode.WRITE))
                .isInstanceOf(EngineException.class);
        assertThatThrownBy(() -> engine.runQuery(details(), "rm photos/x", QueryMode.READ))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("Supported commands");
    }

    @Test
    void streamsAnObject() throws Exception {
        try (StoredObject object = engine.openObject(details(), "photos", "readme.txt")) {
            assertThat(object.contentType()).isEqualTo("text/plain");
            assertThat(object.sizeBytes()).isEqualTo(10L);
            assertThat(object.content().readAllBytes()).hasSize(10);
        }
    }

    @Test
    void reportsMissingObjectsAndRejectsFolderKeys() {
        assertThatThrownBy(() -> engine.openObject(details(), "photos", "nope.txt"))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("NoSuchKey");
        assertThatThrownBy(() -> engine.openObject(details(), "photos", "2024/"))
                .isInstanceOf(EngineException.class);
    }

    @Test
    void surfacesS3ErrorCode() {
        assertThatThrownBy(() -> engine.browse(details(), new ObjectRef("minio", "nope"), 0, 50))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("NoSuchBucket");
    }

    // --- fake S3 -------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(auth == null ? "" : auth);
        String path = exchange.getRequestURI().getPath();
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        if (path.equals("/minio/admin/v3/datausageinfo")) {
            if (adminAvailable) {
                respond(
                        exchange,
                        200,
                        "{\"lastUpdate\":\"2026-11-09T10:00:00Z\",\"objectsCount\":99,"
                                + "\"bucketsUsageInfo\":{\"photos\":{\"size\":123456,\"objectsCount\":99},"
                                + "\"empty\":{\"size\":0,\"objectsCount\":0}}}");
            } else {
                respond(exchange, 403, "");
            }
            return;
        }
        if (path.equals("/")) {
            respond(
                    exchange,
                    200,
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                            + "<Owner><ID>x</ID></Owner><Buckets>"
                            + "<Bucket><Name>empty</Name><CreationDate>2026-01-01T00:00:00.000Z</CreationDate></Bucket>"
                            + "<Bucket><Name>photos</Name><CreationDate>2026-01-02T00:00:00.000Z</CreationDate></Bucket>"
                            + "</Buckets></ListAllMyBucketsResult>");
            return;
        }
        int objectSlash = path.indexOf('/', 1);
        if (objectSlash > 0) {
            String key = path.substring(objectSlash + 1);
            if (path.startsWith("/photos/") && PHOTOS.containsKey(key)) {
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                respond(exchange, 200, "x".repeat(PHOTOS.get(key).intValue()));
            } else {
                respond(
                        exchange,
                        404,
                        "<Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message></Error>");
            }
            return;
        }
        String bucket = path.substring(1);
        Map<String, Long> objects =
                switch (bucket) {
                    case "photos" -> PHOTOS;
                    case "empty" -> Map.of();
                    default -> null;
                };
        if (objects == null) {
            respond(
                    exchange,
                    404,
                    "<Error><Code>NoSuchBucket</Code><Message>The specified bucket does not exist</Message></Error>");
            return;
        }
        respond(exchange, 200, listing(objects, query));
    }

    /**
     * ListObjectsV2 over {@code objects}, two keys per page so continuation tokens are exercised.
     */
    private static String listing(Map<String, Long> objects, Map<String, String> query) {
        String prefix = query.getOrDefault("prefix", "");
        String delimiter = query.get("delimiter");
        List<String> entries = new ArrayList<>();
        Map<String, Long> sizes = new HashMap<>();
        objects.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .sorted()
                .forEach(
                        key -> {
                            String rest = key.substring(prefix.length());
                            int slash = delimiter == null ? -1 : rest.indexOf(delimiter);
                            String entry =
                                    slash >= 0
                                            ? "P" + prefix + rest.substring(0, slash + 1)
                                            : "K" + key;
                            if (!entries.contains(entry)) {
                                entries.add(entry);
                                sizes.put(entry, objects.get(key));
                            }
                        });
        int start =
                query.containsKey("continuation-token")
                        ? Integer.parseInt(query.get("continuation-token"))
                        : 0;
        int end = Math.min(start + 2, entries.size());
        StringBuilder xml =
                new StringBuilder(
                        "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                                + "<Prefix>"
                                + prefix
                                + "</Prefix>");
        for (String entry : entries.subList(start, end)) {
            if (entry.startsWith("P")) {
                xml.append("<CommonPrefixes><Prefix>")
                        .append(entry.substring(1))
                        .append("</Prefix></CommonPrefixes>");
            } else {
                xml.append("<Contents><Key>")
                        .append(entry.substring(1))
                        .append("</Key><LastModified>2026-11-01T12:00:00.000Z</LastModified>")
                        .append("<ETag>&quot;abc&quot;</ETag><Size>")
                        .append(sizes.get(entry))
                        .append("</Size><StorageClass>STANDARD</StorageClass></Contents>");
            }
        }
        boolean truncated = end < entries.size();
        xml.append("<IsTruncated>").append(truncated).append("</IsTruncated>");
        if (truncated) {
            xml.append("<NextContinuationToken>").append(end).append("</NextContinuationToken>");
        }
        return xml.append("</ListBucketResult>").toString();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            query.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return query;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }
}
