package com.janne6565.stratabackend.services.backup;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.engine.ConnectionDetailsResolver;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Scheduled pg_dump backups of every discovered PostgreSQL datasource. Each datasource is backed up
 * independently into its own directory; a datasource whose credentials can't be resolved ({@link
 * BadRequestException} from the resolver — e.g. no backing service or an unresolved credential
 * field) is skipped and logged, never aborting the rest of the run. Non-PostgreSQL engines are
 * ignored.
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
                ConnectionDetails details = connectionResolver.resolve(ds);
                Path dir = directoryFor(ds.getId());
                store.write(dir, tmp -> pgDumpRunner.dumpTo(tmp, details));
                store.prune(dir);
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
