package com.janne6565.stratabackend.services.engine.minio;

import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.janne6565.stratabackend.services.engine.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The handful of read-only S3 calls the MinIO adapter needs (ListBuckets, ListObjectsV2,
 * HeadObject) plus MinIO's admin data-usage endpoint, over the JDK HTTP client with path-style
 * addressing and SigV4 signing ({@link S3Signer}). One instance per operation; it holds the
 * resolved credentials only for the call's lifetime (AUTH.md).
 */
final class S3Client {

    /** MinIO accepts any region unless one is configured; {@code us-east-1} is the S3 default. */
    static final String DEFAULT_REGION = "us-east-1";

    private static final int PAGE_SIZE = 1000;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ConnectionDetails details;

    S3Client(HttpClient http, ObjectMapper mapper, ConnectionDetails details) {
        this.http = http;
        this.mapper = mapper;
        this.details = details;
    }

    record Bucket(String name, String creationDate) {}

    record S3Object(String key, long size, String lastModified, String etag, String storageClass) {}

    /**
     * One listing pass: {@code objects} and (with a delimiter) the folder {@code prefixes}, and
     * whether the scan stopped at the cap before the listing ended.
     */
    record Listing(List<S3Object> objects, List<String> prefixes, boolean truncated) {}

    List<Bucket> listBuckets() {
        Element root = xml(send("GET", "/", Map.of()).body());
        List<Bucket> buckets = new ArrayList<>();
        Element container = child(root, "Buckets");
        if (container != null) {
            for (Element bucket : children(container, "Bucket")) {
                buckets.add(new Bucket(text(bucket, "Name"), text(bucket, "CreationDate")));
            }
        }
        return buckets;
    }

    /**
     * Lists {@code bucket} under {@code prefix}, following continuation tokens until the listing
     * ends or {@code cap} entries (objects + prefixes) have been read.
     *
     * @param delimiter {@code "/"} for a single folder level, {@code null} for a recursive scan
     */
    Listing list(String bucket, String prefix, String delimiter, int cap) {
        List<S3Object> objects = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        String token = null;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("list-type", "2");
            query.put("max-keys", String.valueOf(PAGE_SIZE));
            if (prefix != null && !prefix.isEmpty()) {
                query.put("prefix", prefix);
            }
            if (delimiter != null) {
                query.put("delimiter", delimiter);
            }
            if (token != null) {
                query.put("continuation-token", token);
            }
            Element root = xml(send("GET", "/" + bucket, query).body());
            for (Element content : children(root, "Contents")) {
                objects.add(
                        new S3Object(
                                text(content, "Key"),
                                parseLong(text(content, "Size")),
                                text(content, "LastModified"),
                                stripQuotes(text(content, "ETag")),
                                text(content, "StorageClass")));
            }
            for (Element common : children(root, "CommonPrefixes")) {
                prefixes.add(text(common, "Prefix"));
            }
            boolean more = "true".equalsIgnoreCase(text(root, "IsTruncated"));
            token = text(root, "NextContinuationToken");
            if (!more || token == null || token.isEmpty()) {
                return new Listing(objects, prefixes, false);
            }
            if (objects.size() + prefixes.size() >= cap) {
                return new Listing(objects, prefixes, true);
            }
        }
    }

    /**
     * GET on an object, streaming its body. The caller owns (and must close) the returned stream;
     * the 20s timeout covers only the wait for response headers, not the transfer.
     */
    StoredObject getObject(String bucket, String key) {
        HttpResponse<InputStream> response =
                sendRaw(
                        "GET",
                        "/" + bucket + "/" + key,
                        Map.of(),
                        HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status / 100 != 2) {
            String body;
            try (InputStream in = response.body()) {
                body = new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                body = "";
            }
            throw new EngineException("MinIO returned HTTP " + status + errorSuffix("GET", body));
        }
        HttpHeaders headers = response.headers();
        return new StoredObject(
                headers.firstValue("Content-Type").orElse(null),
                headers.firstValueAsLong("Content-Length").orElse(-1L),
                response.body());
    }

    /** HEAD on an object: its response headers (lower-cased names), or 404 as an engine error. */
    Map<String, List<String>> headObject(String bucket, String key) {
        return send("HEAD", "/" + bucket + "/" + key, Map.of()).headers().map();
    }

    /**
     * MinIO's admin data-usage summary ({@code /minio/admin/v3/datausageinfo}), computed by the
     * server's background scanner — cheap and exact per bucket, but only for credentials with admin
     * rights (e.g. the root user) and only once the first scan cycle has run. Empty otherwise.
     */
    Optional<JsonNode> dataUsage() {
        try {
            HttpResponse<String> response =
                    sendRaw("GET", "/minio/admin/v3/datausageinfo", Map.of());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(mapper.readTree(response.body()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private HttpResponse<String> send(String method, String path, Map<String, String> query) {
        HttpResponse<String> response = sendRaw(method, path, query);
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new EngineException(
                    "MinIO returned HTTP " + status + errorSuffix(method, response.body()));
        }
        return response;
    }

    private HttpResponse<String> sendRaw(String method, String path, Map<String, String> query) {
        return sendRaw(method, path, query, HttpResponse.BodyHandlers.ofString());
    }

    private <T> HttpResponse<T> sendRaw(
            String method,
            String path,
            Map<String, String> query,
            HttpResponse.BodyHandler<T> bodyHandler) {
        String hostHeader = hostHeader();
        String queryString = S3Signer.canonicalQuery(query);
        URI uri =
                URI.create(
                        "http://"
                                + hostHeader
                                + S3Signer.encodePath(path)
                                + (queryString.isEmpty() ? "" : "?" + queryString));
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri)
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(20));
        if (hasCredentials()) {
            S3Signer.sign(
                            method,
                            hostHeader,
                            path,
                            query,
                            details.username(),
                            details.password() == null ? "" : details.password(),
                            DEFAULT_REGION,
                            "s3",
                            Instant.now())
                    .forEach(request::header);
        }
        try {
            return http.send(request.build(), bodyHandler);
        } catch (IOException ex) {
            throw new EngineException("MinIO request failed: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EngineException("MinIO request interrupted");
        }
    }

    private boolean hasCredentials() {
        return details.username() != null && !details.username().isBlank();
    }

    /** Must match what the JDK client sends as {@code Host}: the port is omitted only for :80. */
    private String hostHeader() {
        return details.port() == 80 ? details.host() : details.host() + ":" + details.port();
    }

    /** Surfaces the S3 error {@code <Code>}/{@code <Message>} when the body carries one. */
    private String errorSuffix(String method, String body) {
        if ("HEAD".equals(method) || body == null || body.isBlank()) {
            return "";
        }
        try {
            Element root = xml(body);
            String code = text(root, "Code");
            String message = text(root, "Message");
            if (code == null && message == null) {
                return "";
            }
            return ": " + (code == null ? "" : code + " — ") + (message == null ? "" : message);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static Element xml(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc =
                    factory.newDocumentBuilder()
                            .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            return doc.getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new EngineException("Unreadable MinIO response: " + ex.getMessage());
        }
    }

    /** Direct child elements named {@code name} (not descendants: {@code Prefix} nests). */
    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                out.add(element);
            }
        }
        return out;
    }

    private static Element child(Element parent, String name) {
        List<Element> matches = children(parent, name);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static String text(Element parent, String name) {
        Element element = child(parent, name);
        return element == null ? null : element.getTextContent();
    }

    private static String localName(Element element) {
        String name = element.getTagName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\"", "");
    }
}
