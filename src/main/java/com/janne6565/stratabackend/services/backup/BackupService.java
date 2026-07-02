package com.janne6565.stratabackend.services.backup;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.model.core.BackupResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Scheduled backups of Strata's own state database via pg_dump. File management (atomic write,
 * retention, listing, download resolution) is delegated to {@link BackupStore}; this service owns
 * the app-DB target directory and the pg_dump invocation against Strata's own database. Restore
 * (manual, for disaster recovery): {@code pg_restore --clean --if-exists -d strata <file>}.
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private final PgDumpRunner pgDumpRunner;
    private final BackupProperties properties;
    private final BackupStore store;

    private Path directory() {
        return Path.of(properties.directory());
    }

    /** Writes a new backup and returns its path; throws {@link BackupException} on failure. */
    public Path writeBackup() {
        return store.write(directory(), pgDumpRunner::dumpTo);
    }

    /** Applies the retention policy and removes stale tmp files; returns the deletion count. */
    public int prune() throws IOException {
        return store.prune(directory());
    }

    /** Backup metadata (name, size, timestamp) for the API, newest first. */
    public List<BackupResponse> listBackups() {
        return store.listBackups(directory());
    }

    /** Resolves a backup by file name for download, or 404s. */
    public Path requireBackupFile(String fileName) {
        return store.requireBackupFile(directory(), fileName);
    }
}
