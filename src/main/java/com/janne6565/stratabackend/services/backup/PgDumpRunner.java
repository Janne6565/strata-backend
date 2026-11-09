package com.janne6565.stratabackend.services.backup;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.stereotype.Component;

/**
 * Invokes the {@code pg_dump} binary. The dump uses the custom format ({@code -Fc}: compressed,
 * restorable with {@code pg_restore}). Credentials travel only through the child process
 * environment ({@code PG*} vars) — never on the command line, where they would be visible in the
 * process list. Can target Strata's own database ({@link JdbcConnectionDetails}) or any datasource
 * ({@link ConnectionDetails}).
 */
@Component
@RequiredArgsConstructor
public class PgDumpRunner {

    /** URI query parameters libpq accepts that can plausibly appear in a JDBC URL. */
    private static final Set<String> LIBPQ_PARAMS =
            Set.of(
                    "sslmode",
                    "sslcert",
                    "sslkey",
                    "sslrootcert",
                    "sslcrl",
                    "connect_timeout",
                    "application_name",
                    "options",
                    "target_session_attrs",
                    "gssencmode",
                    "channel_binding",
                    "keepalives",
                    "keepalives_idle",
                    "keepalives_interval",
                    "keepalives_count");

    private final BackupProperties properties;
    private final JdbcConnectionDetails connectionDetails;

    /** Dumps Strata's own state database to {@code target}. */
    public void dumpTo(Path target) {
        String libpqUri = toLibpqUri(connectionDetails.getJdbcUrl());
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PGUSER", connectionDetails.getUsername());
        env.put("PGPASSWORD", connectionDetails.getPassword());
        dump(target, libpqUri, env);
    }

    /**
     * {@code jdbc:postgresql://host:port/db?…} minus the {@code jdbc:} prefix is a libpq URI, but
     * pg_dump rejects query parameters only pgjdbc knows (e.g. {@code loggerLevel}, which
     * Testcontainers appends), so only parameters libpq understands are kept.
     */
    static String toLibpqUri(String jdbcUrl) {
        String uri = jdbcUrl.replaceFirst("^jdbc:", "");
        int query = uri.indexOf('?');
        if (query < 0) {
            return uri;
        }
        String kept =
                Arrays.stream(uri.substring(query + 1).split("&"))
                        .filter(param -> LIBPQ_PARAMS.contains(param.split("=", 2)[0]))
                        .collect(Collectors.joining("&"));
        return uri.substring(0, query) + (kept.isEmpty() ? "" : "?" + kept);
    }

    /** Dumps an arbitrary datasource (e.g. a discovered PostgreSQL database) to {@code target}. */
    public void dumpTo(Path target, ConnectionDetails details) {
        String libpqUri =
                "postgresql://" + details.host() + ":" + details.port() + "/" + details.database();
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PGHOST", details.host());
        env.put("PGPORT", String.valueOf(details.port()));
        env.put("PGDATABASE", details.database());
        if (details.username() != null) {
            env.put("PGUSER", details.username());
        }
        if (details.password() != null) {
            env.put("PGPASSWORD", details.password());
        }
        dump(target, libpqUri, env);
    }

    private void dump(Path target, String libpqUri, Map<String, String> pgEnv) {
        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        List.of(
                                properties.pgDumpPath(),
                                "--format=custom",
                                "--file=" + target.toAbsolutePath(),
                                libpqUri));
        processBuilder.environment().putAll(pgEnv);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            // Drain output concurrently: reading inline would block until process exit and
            // defeat the timeout below; not draining at all could deadlock a chatty pg_dump
            // on a full pipe buffer.
            CompletableFuture<String> output =
                    CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new BackupException("pg_dump timed out after " + properties.timeout());
            }
            if (process.exitValue() != 0) {
                throw new BackupException(
                        "pg_dump exited with code "
                                + process.exitValue()
                                + ": "
                                + output.completeOnTimeout("", 5, TimeUnit.SECONDS).join());
            }
        } catch (IOException e) {
            throw new BackupException("Failed to start pg_dump: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupException("Interrupted while waiting for pg_dump", e);
        }
    }

    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return "";
        }
    }
}
