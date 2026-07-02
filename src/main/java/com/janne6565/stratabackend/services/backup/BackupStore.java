package com.janne6565.stratabackend.services.backup;

import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.services.backup.BackupRetentionPolicy.StoredBackup;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Directory-level backup file management, shared by the app-DB backup and per-datasource backups.
 * Each backup is written to a {@code .tmp} file and atomically renamed only after the dump
 * succeeds, so a crash mid-write can never leave a truncated file that looks like a valid backup.
 * All methods operate on an explicit target directory; the caller owns the directory layout.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupStore {

    /** Supplies the dump bytes into a caller-chosen temp file (e.g. a pg_dump invocation). */
    @FunctionalInterface
    public interface DumpInto {
        void dumpInto(Path tmp) throws IOException;
    }

    /** Tmp files older than this are considered crash leftovers and pruned. */
    private static final Duration STALE_TMP_AGE = Duration.ofHours(1);

    private final BackupRetentionPolicy retentionPolicy;
    private final Clock clock;

    /** Writes a new backup into {@code directory} and returns its path. */
    public Path write(Path directory, DumpInto dump) {
        String fileName = BackupFileNames.fileName(clock.instant());
        Path target = directory.resolve(fileName);
        Path tmp = directory.resolve(fileName + BackupFileNames.TMP_SUFFIX);
        try {
            Files.createDirectories(directory);
            dump.dumpInto(tmp);
            move(tmp, target);
            return target;
        } catch (IOException | BackupException e) {
            deleteQuietly(tmp);
            throw e instanceof BackupException backupException
                    ? backupException
                    : new BackupException("Failed to write backup: " + e.getMessage(), e);
        }
    }

    /** Applies the retention policy and removes stale tmp files; returns the deletion count. */
    public int prune(Path directory) throws IOException {
        Instant now = clock.instant();
        List<StoredBackup> backups = list(directory);
        List<StoredBackup> deletions = retentionPolicy.selectDeletions(backups, now);
        for (StoredBackup deletion : deletions) {
            Files.deleteIfExists(deletion.path());
        }
        int staleTmpFiles = pruneStaleTmpFiles(directory, now);
        log.info(
                "Backup prune ({}): kept {}, deleted {} (+ {} stale tmp files)",
                directory,
                backups.size() - deletions.size(),
                deletions.size(),
                staleTmpFiles);
        return deletions.size() + staleTmpFiles;
    }

    /** Backup metadata (name, size, timestamp) for the API, newest first. */
    public List<BackupResponse> listBackups(Path directory) {
        try {
            List<BackupResponse> responses = new ArrayList<>();
            for (StoredBackup backup : list(directory)) {
                responses.add(
                        new BackupResponse(
                                backup.path().getFileName().toString(),
                                Files.size(backup.path()),
                                backup.createdAt()));
            }
            return responses;
        } catch (IOException e) {
            throw new BackupException("Failed to list backups: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a backup by file name for download, or 404s. The name is validated against the
     * strict backup-name pattern (which excludes path separators and {@code ..}) and matched
     * against the actual directory listing, so it cannot be used to read files outside {@code
     * directory}.
     */
    public Path requireBackupFile(Path directory, String fileName) {
        if (BackupFileNames.parse(fileName).isEmpty()) {
            throw new NotFoundException("Backup not found: " + fileName);
        }
        try {
            return list(directory).stream()
                    .map(StoredBackup::path)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Backup not found: " + fileName));
        } catch (IOException e) {
            throw new BackupException("Failed to read backups: " + e.getMessage(), e);
        }
    }

    /** All well-formed backups in {@code directory}, newest first. */
    public List<StoredBackup> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.flatMap(
                            path ->
                                    BackupFileNames.parse(path.getFileName().toString())
                                            .map(createdAt -> new StoredBackup(path, createdAt))
                                            .stream())
                    .sorted(Comparator.comparing(StoredBackup::createdAt).reversed())
                    .toList();
        }
    }

    private int pruneStaleTmpFiles(Path directory, Instant now) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int deleted = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path path : files.toList()) {
                if (path.getFileName().toString().endsWith(BackupFileNames.TMP_SUFFIX)
                        && Files.getLastModifiedTime(path)
                                .toInstant()
                                .isBefore(now.minus(STALE_TMP_AGE))
                        && Files.deleteIfExists(path)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Same-directory rename is atomic on all common PVC file systems anyway.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not clean up backup tmp file {}", path, e);
        }
    }
}
