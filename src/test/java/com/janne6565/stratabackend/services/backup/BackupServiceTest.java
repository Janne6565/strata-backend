package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.model.core.BackupResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BackupService is a thin adapter over {@link BackupStore} bound to the app-DB directory; the
 * directory mechanics themselves are covered by {@link BackupStoreTest}. These tests pin the
 * delegation (right directory, pg_dump wiring, return passthrough).
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    private static final Path DIR = Path.of("/backups");

    @Mock private PgDumpRunner pgDumpRunner;
    @Mock private BackupStore store;

    private BackupService backupService;

    @BeforeEach
    void setUp() {
        BackupProperties properties =
                new BackupProperties(
                        false,
                        DIR.toString(),
                        "0 0 */2 * * *",
                        List.of(Duration.ofHours(2)),
                        "pg_dump",
                        Duration.ofMinutes(5));
        backupService = new BackupService(pgDumpRunner, properties, store);
    }

    @Test
    void writeBackupDelegatesToStoreForTheAppDirectory() {
        Path expected = DIR.resolve("strata-backup.dump");
        when(store.write(eq(DIR), any())).thenReturn(expected);

        assertThat(backupService.writeBackup()).isEqualTo(expected);
        verify(store).write(eq(DIR), any());
    }

    @Test
    void pruneDelegatesToStore() throws IOException {
        when(store.prune(DIR)).thenReturn(3);

        assertThat(backupService.prune()).isEqualTo(3);
    }

    @Test
    void listBackupsDelegatesToStore() {
        List<BackupResponse> expected =
                List.of(new BackupResponse("strata-backup.dump", 10, Instant.EPOCH));
        when(store.listBackups(DIR)).thenReturn(expected);

        assertThat(backupService.listBackups()).isEqualTo(expected);
    }

    @Test
    void requireBackupFileDelegatesToStore() {
        Path expected = DIR.resolve("strata-backup.dump");
        when(store.requireBackupFile(DIR, "strata-backup.dump")).thenReturn(expected);

        assertThat(backupService.requireBackupFile("strata-backup.dump")).isEqualTo(expected);
    }
}
