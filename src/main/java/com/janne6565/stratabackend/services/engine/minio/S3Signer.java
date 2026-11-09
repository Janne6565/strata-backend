package com.janne6565.stratabackend.services.engine.minio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Signature Version 4 for body-less S3 requests (GET/HEAD), which is all the read-only MinIO
 * adapter issues. Kept dependency-free on purpose, like the Loki adapter's plain HTTP client: the
 * MinIO/AWS SDKs would drag in OkHttp/Netty for a handful of list calls. Signs the {@code host},
 * {@code x-amz-content-sha256} and {@code x-amz-date} headers with an empty payload hash.
 */
final class S3Signer {

    static final String EMPTY_PAYLOAD_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private S3Signer() {}

    /**
     * Computes the headers to attach to a request.
     *
     * @param method HTTP method (GET/HEAD)
     * @param host the exact {@code Host} header value the client sends ({@code host[:port]})
     * @param path the raw, unencoded path (e.g. {@code /bucket}); encoded here per SigV4 rules
     * @param query unencoded query parameters (empty-valued keys like {@code lifecycle} allowed)
     * @return {@code Authorization}, {@code x-amz-date} and {@code x-amz-content-sha256} headers
     */
    static Map<String, String> sign(
            String method,
            String host,
            String path,
            Map<String, String> query,
            String accessKey,
            String secretKey,
            String region,
            String service,
            Instant now) {
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders =
                "host:"
                        + host
                        + "\n"
                        + "x-amz-content-sha256:"
                        + EMPTY_PAYLOAD_SHA256
                        + "\n"
                        + "x-amz-date:"
                        + amzDate
                        + "\n";
        String canonicalRequest =
                method
                        + "\n"
                        + encodePath(path)
                        + "\n"
                        + canonicalQuery(query)
                        + "\n"
                        + canonicalHeaders
                        + "\n"
                        + signedHeaders
                        + "\n"
                        + EMPTY_PAYLOAD_SHA256;
        String stringToSign =
                ALGORITHM + "\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);

        byte[] key = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(key, stringToSign));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(
                "Authorization",
                ALGORITHM
                        + " Credential="
                        + accessKey
                        + "/"
                        + scope
                        + ", SignedHeaders="
                        + signedHeaders
                        + ", Signature="
                        + signature);
        headers.put("x-amz-date", amzDate);
        headers.put("x-amz-content-sha256", EMPTY_PAYLOAD_SHA256);
        return headers;
    }

    /** Sorted, RFC 3986-encoded {@code k=v&…}; also used to build the request URL itself. */
    static String canonicalQuery(Map<String, String> query) {
        return new TreeMap<>(query)
                .entrySet().stream()
                        .map(
                                e ->
                                        encode(e.getKey())
                                                + "="
                                                + encode(e.getValue() == null ? "" : e.getValue()))
                        .collect(Collectors.joining("&"));
    }

    /** Encodes each path segment, keeping the {@code /} separators. */
    static String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        StringBuilder out = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if (c == '/' || isUnreserved(c)) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return out.toString();
    }

    /** RFC 3986 percent-encoding: everything but {@code A-Z a-z 0-9 - _ . ~}. */
    static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if (isUnreserved(c)) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return out.toString();
    }

    private static boolean isUnreserved(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '-'
                || c == '_'
                || c == '.'
                || c == '~';
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
