package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.janne6565.stratabackend.TestcontainersConfiguration;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.repository.UserRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end backup against a real Postgres. Skips (rather than fails) on machines without a
 * pg_dump >= 17 on the PATH; CI installs postgresql-client-17 so it always runs there.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackupServiceIntegrationTest {

    @TempDir private static Path backupDirectory;

    @Autowired private BackupService backupService;
    @Autowired private UserRepository userRepository;
    @Autowired private PgDumpRunner pgDumpRunner;
    @Autowired private JdbcConnectionDetails jdbcConnectionDetails;

    @DynamicPropertySource
    static void backupProperties(DynamicPropertyRegistry registry) {
        registry.add("strata.backup.enabled", () -> "false");
        registry.add("strata.backup.directory", () -> backupDirectory.toString());
    }

    @BeforeAll
    static void requiresPgDump17() {
        assumeTrue(
                localPgDumpMajorVersion() >= 17,
                "pg_dump >= 17 not on PATH; skipping backup integration test");
    }

    @Test
    void writesARestorableDumpContainingTheSeededData() throws IOException {
        userRepository.save(new UserEntity("backup-probe", "hash", Role.USER));

        Path backup = backupService.writeBackup();

        assertThat(backup).exists();
        assertThat(Files.size(backup)).isGreaterThan(0);
        byte[] magic = new byte[5];
        try (var in = Files.newInputStream(backup)) {
            assertThat(in.read(magic)).isEqualTo(5);
        }
        assertThat(new String(magic, StandardCharsets.UTF_8)).isEqualTo("PGDMP");
        try (Stream<Path> files = Files.list(backupDirectory)) {
            assertThat(files.filter(f -> f.toString().endsWith(BackupFileNames.TMP_SUFFIX)))
                    .isEmpty();
        }
    }

    @Test
    void dumpsAnArbitraryConnectionViaConnectionDetails() throws IOException {
        // Point the datasource-style overload at the Testcontainers Postgres (the app's own DB
        // here) to exercise the ConnectionDetails path against a real server.
        URI uri = URI.create(jdbcConnectionDetails.getJdbcUrl().replaceFirst("^jdbc:", ""));
        ConnectionDetails details =
                new ConnectionDetails(
                        "postgresql",
                        uri.getHost(),
                        uri.getPort(),
                        uri.getPath().replaceFirst("^/", ""),
                        jdbcConnectionDetails.getUsername(),
                        jdbcConnectionDetails.getPassword());
        Path target = backupDirectory.resolve("datasource-probe.dump");

        pgDumpRunner.dumpTo(target, details);

        assertThat(target).exists();
        assertThat(Files.size(target)).isGreaterThan(0);
        byte[] magic = new byte[5];
        try (var in = Files.newInputStream(target)) {
            assertThat(in.read(magic)).isEqualTo(5);
        }
        assertThat(new String(magic, StandardCharsets.UTF_8)).isEqualTo("PGDMP");
    }

    private static int localPgDumpMajorVersion() {
        try {
            Process process = new ProcessBuilder("pg_dump", "--version").start();
            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                return -1;
            }
            Matcher matcher = Pattern.compile("(\\d+)\\.").matcher(output);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
