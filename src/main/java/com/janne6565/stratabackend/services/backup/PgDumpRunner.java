package com.janne6565.stratabackend.services.backup;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.stereotype.Component;

/**
 * Invokes the {@code pg_dump} binary against the application database. The dump uses the custom
 * format ({@code -Fc}: compressed, restorable with {@code pg_restore}). Credentials travel only
 * through the child process environment ({@code PGUSER}/{@code PGPASSWORD}) — never on the command
 * line, where they would be visible in the process list. Connection details come from {@link
 * JdbcConnectionDetails} so Testcontainers-backed tests target the right database.
 */
@Component
@RequiredArgsConstructor
public class PgDumpRunner {

    private final BackupProperties properties;
    private final JdbcConnectionDetails connectionDetails;

    /** Runs pg_dump writing to {@code target}; throws {@link BackupException} on any failure. */
    public void dumpTo(Path target) {
        // jdbc:postgresql://host:port/db minus the "jdbc:" prefix is a valid libpq URI.
        String libpqUri = connectionDetails.getJdbcUrl().replaceFirst("^jdbc:", "");
        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        List.of(
                                properties.pgDumpPath(),
                                "--format=custom",
                                "--file=" + target.toAbsolutePath(),
                                libpqUri));
        processBuilder.environment().put("PGUSER", connectionDetails.getUsername());
        processBuilder.environment().put("PGPASSWORD", connectionDetails.getPassword());
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
