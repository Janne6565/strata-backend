package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.DatasourceBackupApi;
import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.security.authorization.ResourceId;
import com.janne6565.stratabackend.services.backup.DatasourceBackupService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements {@link DatasourceBackupApi}. Both operations are gated on read access to the target
 * datasource via {@link NeedsValidation} + {@link ResourceId}. The download file name is validated
 * inside {@link DatasourceBackupService} against the strict backup-name pattern, so it cannot
 * escape the datasource's backup directory.
 */
@RestController
@RequiredArgsConstructor
public class DatasourceBackupController implements DatasourceBackupApi {

    private final DatasourceBackupService datasourceBackupService;

    @Override
    @NeedsValidation(Operation.DATASOURCE_BACKUP_LIST)
    public List<BackupResponse> list(@ResourceId UUID id) {
        return datasourceBackupService.listBackups(id);
    }

    @Override
    @NeedsValidation(Operation.DATASOURCE_BACKUP_CREATE)
    public BackupResponse create(@ResourceId UUID id) {
        return datasourceBackupService.backupNow(id);
    }

    @Override
    @NeedsValidation(Operation.DATASOURCE_BACKUP_DOWNLOAD)
    public ResponseEntity<Resource> download(@ResourceId UUID id, String name) {
        Path file = datasourceBackupService.requireBackupFile(id, name);
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .body(resource);
    }
}
