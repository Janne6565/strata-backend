package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class BackupStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-02T14:00:00Z");

    @TempDir private Path directory;

    private BackupStore store;

    @BeforeEach
    void setUp() {
        BackupProperties properties =
                new BackupProperties(
                        false,
                        directory.toString(),
                        "0 0 */2 * * *",
                        List.of(Duration.ofHours(2), Duration.ofDays(1), Duration.ofDays(14)),
                        "pg_dump",
                        Duration.ofMinutes(5));
        store =
                new BackupStore(
                        new BackupRetentionPolicy(properties), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void writeCreatesTheDirectoryAndRenamesTmpToFinalFile() throws IOException {
        Path sub = directory.resolve("datasources").resolve("abc");

        Path written = store.write(sub, tmp -> Files.writeString(tmp, "PGDMP-content"));

        assertThat(written).isEqualTo(sub.resolve("strata-backup-20260702T140000Z.dump"));
        assertThat(written).exists().content().isEqualTo("PGDMP-content");
        assertThat(tmpFiles(sub)).isEmpty();
    }

    @Test
    void failedDumpLeavesNoFilesBehind() throws IOException {
        assertThatThrownBy(
                        () ->
                                store.write(
                                        directory,
                                        tmp -> {
                                            Files.writeString(tmp, "partial");
                                            throw new BackupException("pg_dump exited with code 1");
                                        }))
                .isInstanceOf(BackupException.class);

        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void ioFailureFromTheDumpIsWrappedInBackupException() {
        assertThatThrownBy(
                        () ->
                                store.write(
                                        directory,
                                        tmp -> {
                                            throw new IOException("disk gone");
                                        }))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("disk gone");
    }

    @Test
    void pruneDeletesExactlyWhatThePolicySelects() throws IOException {
        Path newest = existingBackup(directory, Duration.ofMinutes(10));
        Path bucketKeeper = existingBackup(directory, Duration.ofHours(1));
        Path doomed = existingBackup(directory, Duration.ofMinutes(30));
        Path expired = existingBackup(directory, Duration.ofDays(20));
        Path foreign = Files.writeString(directory.resolve("not-a-backup.txt"), "keep me");

        int deleted = store.prune(directory);

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

        store.prune(directory);

        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();
    }

    @Test
    void listBackupsExposesNameSizeAndTimestampNewestFirst() throws IOException {
        Path older = existingBackup(directory, Duration.ofHours(3));
        Files.writeString(older, "twelve-bytes");
        existingBackup(directory, Duration.ofMinutes(10));
        Files.writeString(directory.resolve("garbage.bin"), "x");

        var backups = store.listBackups(directory);

        assertThat(backups).hasSize(2);
        assertThat(backups.get(0).createdAt()).isAfter(backups.get(1).createdAt());
        assertThat(backups.get(1).name()).isEqualTo(older.getFileName().toString());
        assertThat(backups.get(1).sizeBytes()).isEqualTo("twelve-bytes".length());
    }

    @Test
    void listOfAMissingDirectoryIsEmpty() throws IOException {
        assertThat(store.listBackups(directory.resolve("does-not-exist"))).isEmpty();
        assertThat(store.list(directory.resolve("does-not-exist"))).isEmpty();
    }

    @Test
    void requireBackupFileReturnsAnExistingBackup() throws IOException {
        Path backup = existingBackup(directory, Duration.ofMinutes(10));

        assertThat(store.requireBackupFile(directory, backup.getFileName().toString()))
                .isEqualTo(backup);
    }

    @Test
    void requireBackupFileRejectsPathTraversalAndForeignNames() {
        assertThatThrownBy(() -> store.requireBackupFile(directory, "../../etc/passwd"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.requireBackupFile(directory, "garbage.bin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireBackupFile404sWhenAValidNameIsNotOnDisk() {
        String missing = BackupFileNames.fileName(NOW.minus(Duration.ofDays(1)));

        assertThatThrownBy(() -> store.requireBackupFile(directory, missing))
                .isInstanceOf(NotFoundException.class);
    }

    private Path existingBackup(Path dir, Duration age) throws IOException {
        Path path = dir.resolve(BackupFileNames.fileName(NOW.minus(age)));
        Files.writeString(path, "dump");
        return path;
    }

    private List<Path> tmpFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .endsWith(BackupFileNames.TMP_SUFFIX))
                    .toList();
        }
    }
}
