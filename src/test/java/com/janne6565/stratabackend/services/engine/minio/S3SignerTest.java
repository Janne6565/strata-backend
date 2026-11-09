package com.janne6565.stratabackend.services.engine.minio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Checks the signer against the worked examples in the AWS S3 SigV4 documentation ("Signature
 * Calculations for the Authorization Header: Transferring Payload in a Single Chunk").
 */
class S3SignerTest {

    private static final String ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final Instant DATE = Instant.parse("2013-05-24T00:00:00Z");
    private static final String HOST = "examplebucket.s3.amazonaws.com";

    @Test
    void signsListObjectsExample() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("prefix", "J");
        query.put("max-keys", "2");

        Map<String, String> headers =
                S3Signer.sign(
                        "GET", HOST, "/", query, ACCESS_KEY, SECRET_KEY, "us-east-1", "s3", DATE);

        assertThat(headers.get("Authorization"))
                .isEqualTo(
                        "AWS4-HMAC-SHA256"
                                + " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,"
                                + " SignedHeaders=host;x-amz-content-sha256;x-amz-date,"
                                + " Signature=34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7");
        assertThat(headers.get("x-amz-date")).isEqualTo("20130524T000000Z");
        assertThat(headers.get("x-amz-content-sha256")).isEqualTo(S3Signer.EMPTY_PAYLOAD_SHA256);
    }

    @Test
    void signsGetBucketLifecycleExampleWithValuelessParameter() {
        Map<String, String> headers =
                S3Signer.sign(
                        "GET",
                        HOST,
                        "/",
                        Map.of("lifecycle", ""),
                        ACCESS_KEY,
                        SECRET_KEY,
                        "us-east-1",
                        "s3",
                        DATE);

        assertThat(headers.get("Authorization"))
                .endsWith(
                        "Signature=fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543");
    }

    @Test
    void encodesPathSegmentsButKeepsSlashes() {
        assertThat(S3Signer.encodePath("/bucket/my photos/ä+b.txt"))
                .isEqualTo("/bucket/my%20photos/%C3%A4%2Bb.txt");
        assertThat(S3Signer.encodePath("")).isEqualTo("/");
    }

    @Test
    void canonicalQueryIsSortedAndEncoded() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("prefix", "a b/");
        query.put("delimiter", "/");
        query.put("list-type", "2");
        assertThat(S3Signer.canonicalQuery(query))
                .isEqualTo("delimiter=%2F&list-type=2&prefix=a%20b%2F");
    }
}
