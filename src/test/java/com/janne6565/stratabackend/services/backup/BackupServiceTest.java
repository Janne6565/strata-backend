package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-02T14:00:00Z");

    @TempDir private Path directory;

    private PgDumpRunner pgDumpRunner;
    private BackupService backupService;

    @BeforeEach
    void setUp() {
        pgDumpRunner = mock(PgDumpRunner.class);
        BackupProperties properties =
                new BackupProperties(
                        false,
                        directory.toString(),
                        "0 0 */2 * * *",
                        List.of(Duration.ofHours(2), Duration.ofDays(1), Duration.ofDays(14)),
                        "pg_dump",
                        Duration.ofMinutes(5));
        backupService =
                new BackupService(
                        pgDumpRunner,
                        properties,
                        new BackupRetentionPolicy(properties),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void writeBackupRenamesTmpToFinalFile() throws IOException {
        doAnswer(
                        invocation -> {
                            Files.writeString(invocation.getArgument(0), "PGDMP-content");
                            return null;
                        })
                .when(pgDumpRunner)
                .dumpTo(any());

        Path written = backupService.writeBackup();

        assertThat(written).isEqualTo(directory.resolve("strata-backup-20260702T140000Z.dump"));
        assertThat(written).exists().content().isEqualTo("PGDMP-content");
        assertThat(tmpFiles()).isEmpty();
    }

    @Test
    void failedDumpLeavesNoFilesBehind() throws IOException {
        doAnswer(
                        invocation -> {
                            Files.writeString(invocation.getArgument(0), "partial");
                            throw new BackupException("pg_dump exited with code 1: boom");
                        })
                .when(pgDumpRunner)
                .dumpTo(any());

        assertThatThrownBy(() -> backupService.writeBackup()).isInstanceOf(BackupException.class);

        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void ioFailureIsWrappedInBackupException() {
        doThrow(new BackupException("Failed to start pg_dump: not found"))
                .when(pgDumpRunner)
                .dumpTo(any());

        assertThatThrownBy(() -> backupService.writeBackup())
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void pruneDeletesExactlyWhatThePolicySelects() throws IOException {
        Path newest = existingBackup(Duration.ofMinutes(10));
        Path bucketKeeper = existingBackup(Duration.ofHours(1));
        Path doomed = existingBackup(Duration.ofMinutes(30));
        Path expired = existingBackup(Duration.ofDays(20));
        Path foreign = Files.writeString(directory.resolve("not-a-backup.txt"), "keep me");

        int deleted = backupService.prune();

        assertThat(deleted).isEqualTo(2);
        assertThat(newest).exists();
        assertThat(bucketKeeper).exists();
        assertThat(foreign).exists();
        assertThat(doomed).doesNotExist();
        assertThat(expired).doesNotExist();
    }

    @Test
    void pruneRemovesStaleTmpFilesButKeepsFreshOnes() throws IOException {
        Path stale = directory.resolve("strata-backup-20260701T000000Z.dump.tmp");
        Files.writeString(stale, "crashed");
        Files.setLastModifiedTime(stale, FileTime.from(NOW.minus(Duration.ofHours(2))));
        Path fresh = directory.resolve("strata-backup-20260702T135500Z.dump.tmp");
        Files.writeString(fresh, "in progress");
        Files.setLastModifiedTime(fresh, FileTime.from(NOW.minus(Duration.ofMinutes(5))));

        backupService.prune();

        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();
    }

    @Test
    void listReturnsBackupsNewestFirst() throws IOException {
        existingBackup(Duration.ofHours(3));
        existingBackup(Duration.ofMinutes(10));
        Files.writeString(directory.resolve("garbage.bin"), "x");

        var backups = backupService.list();

        assertThat(backups).hasSize(2);
        assertThat(backups.get(0).createdAt()).isAfter(backups.get(1).createdAt());
    }

    @Test
    void listBackupsExposesNameSizeAndTimestampNewestFirst() throws IOException {
        Path older = existingBackup(Duration.ofHours(3));
        Files.writeString(older, "twelve-bytes");
        existingBackup(Duration.ofMinutes(10));
        Files.writeString(directory.resolve("garbage.bin"), "x");

        var backups = backupService.listBackups();

        assertThat(backups).hasSize(2);
        assertThat(backups.get(0).createdAt()).isAfter(backups.get(1).createdAt());
        assertThat(backups.get(1).name()).isEqualTo(older.getFileName().toString());
        assertThat(backups.get(1).sizeBytes()).isEqualTo("twelve-bytes".length());
    }

    @Test
    void requireBackupFileReturnsAnExistingBackup() throws IOException {
        Path backup = existingBackup(Duration.ofMinutes(10));

        assertThat(backupService.requireBackupFile(backup.getFileName().toString()))
                .isEqualTo(backup);
    }

    @Test
    void requireBackupFileRejectsPathTraversalAndForeignNames() {
        assertThatThrownBy(() -> backupService.requireBackupFile("../../etc/passwd"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> backupService.requireBackupFile("garbage.bin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireBackupFile404sWhenAValidNameIsNotOnDisk() {
        String missing = BackupFileNames.fileName(NOW.minus(Duration.ofDays(1)));

        assertThatThrownBy(() -> backupService.requireBackupFile(missing))
                .isInstanceOf(NotFoundException.class);
    }

    private Path existingBackup(Duration age) throws IOException {
        Path path = directory.resolve(BackupFileNames.fileName(NOW.minus(age)));
        Files.writeString(path, "dump");
        return path;
    }

    private List<Path> tmpFiles() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .endsWith(BackupFileNames.TMP_SUFFIX))
                    .toList();
        }
    }
}
