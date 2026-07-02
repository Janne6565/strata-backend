package com.janne6565.stratabackend.services.backup;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the periodic backup run. Kept separate from the services so backups stay triggerable (e.g.
 * by a future admin endpoint) even when scheduling is disabled. Each stage is isolated: a failed
 * app-DB dump must not stop pruning or the datasource backups, and no exception may escape the
 * scheduled method.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "strata.backup", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;
    private final DatasourceBackupService datasourceBackupService;

    @Scheduled(cron = "${strata.backup.cron}")
    public void run() {
        try {
            Path backup = backupService.writeBackup();
            log.info("Backup written: {} ({} bytes)", backup, Files.size(backup));
        } catch (Exception e) {
            log.error("Backup failed", e);
        }
        try {
            backupService.prune();
        } catch (Exception e) {
            log.error("Backup prune failed", e);
        }
        try {
            datasourceBackupService.backupAll();
        } catch (Exception e) {
            log.error("Datasource backups failed", e);
        }
    }
}
