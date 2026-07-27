package com.janne6565.stratabackend.services.backup;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.engine.ConnectionDetailsResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * pg_dump backups of discovered PostgreSQL datasources — both the scheduled sweep ({@link
 * #backupAll()}) and on-demand single-datasource runs ({@link #backupNow(UUID)}). Each datasource
 * is backed up independently into its own directory. In the scheduled sweep a datasource whose
 * credentials can't be resolved ({@link BadRequestException} from the resolver — e.g. no backing
 * service or an unresolved credential field) is skipped and logged; on a manual run that same error
 * is surfaced to the caller. Non-PostgreSQL engines are not backed up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceBackupService {

    private static final String POSTGRES_DRIVER = "postgresql";
    private static final String DATASOURCES_SUBDIR = "datasources";

    private final DatasourceRepository datasourceRepository;
    private final ConnectionDetailsResolver connectionResolver;
    private final PgDumpRunner pgDumpRunner;
    private final BackupStore store;
    private final BackupProperties properties;

    /** Backs up every discovered PostgreSQL datasource. Never throws; failures are logged. */
    public void backupAll() {
        List<DatasourceEntity> targets =
                datasourceRepository.findAll().stream()
                        .filter(ds -> POSTGRES_DRIVER.equals(ds.getDriver()))
                        .toList();
        log.info("Starting backup of {} PostgreSQL datasource(s)", targets.size());

        int ok = 0;
        int skipped = 0;
        int failed = 0;
        for (DatasourceEntity ds : targets) {
            try {
                backupDatasource(ds);
                ok++;
            } catch (BadRequestException e) {
                log.warn(
                        "Skipping backup for datasource {} ({}): {}",
                        ds.getId(),
                        ds.getDisplayName(),
                        e.getMessage());
                skipped++;
            } catch (Exception e) {
                log.error(
                        "Backup failed for datasource {} ({})", ds.getId(), ds.getDisplayName(), e);
                failed++;
            }
        }
        log.info("Datasource backups complete: {} ok, {} skipped, {} failed", ok, skipped, failed);
    }

    /**
     * Backs up a single PostgreSQL datasource now and returns the new backup's metadata. Surfaces
     * {@link NotFoundException} (unknown datasource) and {@link BadRequestException}
     * (non-PostgreSQL engine, or credentials that can't be resolved) to the caller.
     */
    public BackupResponse backupNow(UUID datasourceId) {
        DatasourceEntity ds =
                datasourceRepository
                        .findById(datasourceId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Datasource not found: " + datasourceId));
        if (!POSTGRES_DRIVER.equals(ds.getDriver())) {
            throw new BadRequestException("Backups are only supported for PostgreSQL datasources");
        }
        Path written = backupDatasource(ds);
        log.info("Manual backup written for datasource {}: {}", ds.getId(), written.getFileName());
        String name = written.getFileName().toString();
        return listBackups(datasourceId).stream()
                .filter(backup -> backup.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new BackupException("Backup written but not found: " + name));
    }

    /** Dumps one datasource into its directory and prunes; returns the written path. */
    private Path backupDatasource(DatasourceEntity ds) {
        ConnectionDetails details = connectionResolver.resolve(ds);
        Path dir = directoryFor(ds.getId());
        Path written = store.write(dir, tmp -> pgDumpRunner.dumpTo(tmp, details));
        try {
            store.prune(dir);
        } catch (IOException e) {
            // A prune failure must not fail the backup — the dump itself succeeded.
            log.warn("Prune after backup failed for datasource {}", ds.getId(), e);
        }
        return written;
    }

    /**
     * Prunes every datasource backup directory on disk, not just the ones dumped this run.
     *
     * <p>{@link #backupDatasource} only prunes after a successful dump, so a datasource that stops
     * being backed up — credentials no longer resolvable, workload gone MISSING, engine no longer
     * PostgreSQL — would keep its existing dumps forever. This sweep is driven by the directory
     * listing instead, so those directories keep ageing out under the retention tiers.
     *
     * <p>A directory whose id is no longer in the catalog is orphaned: {@code DiscoveryService}
     * never deletes rows (unmatched workloads are marked MISSING), so the row can only be gone
     * because someone deleted the datasource deliberately. Those prune without the newest-backup
     * safety net and the directory is removed once it has drained. Never throws; failures are
     * logged per directory so one bad directory cannot stop the rest.
     */
    public void pruneAll() {
        Path root = Path.of(properties.directory(), DATASOURCES_SUBDIR);
        if (!Files.isDirectory(root)) {
            return;
        }
        Set<String> known =
                datasourceRepository.findAll().stream()
                        .map(ds -> ds.getId().toString())
                        .collect(Collectors.toSet());

        List<Path> directories;
        try (Stream<Path> entries = Files.list(root)) {
            directories = entries.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.error("Could not list datasource backup directories in {}", root, e);
            return;
        }

        int swept = 0;
        int orphaned = 0;
        int removed = 0;
        for (Path dir : directories) {
            boolean isKnown = known.contains(dir.getFileName().toString());
            try {
                store.prune(dir, isKnown);
                if (!isKnown) {
                    orphaned++;
                    if (store.deleteIfEmpty(dir)) {
                        removed++;
                    }
                }
                swept++;
            } catch (IOException e) {
                log.warn("Prune sweep failed for backup directory {}", dir, e);
            }
        }
        log.info(
                "Datasource backup prune sweep complete: {} directories swept ({} orphaned, {}"
                        + " removed after draining)",
                swept,
                orphaned,
                removed);
    }

    /** Backup metadata for one datasource, newest first. */
    public List<BackupResponse> listBackups(UUID datasourceId) {
        return store.listBackups(directoryFor(datasourceId));
    }

    /** Resolves a datasource backup by file name for download, or 404s. */
    public Path requireBackupFile(UUID datasourceId, String fileName) {
        return store.requireBackupFile(directoryFor(datasourceId), fileName);
    }

    private Path directoryFor(UUID datasourceId) {
        return Path.of(properties.directory(), DATASOURCES_SUBDIR, datasourceId.toString());
    }
}
