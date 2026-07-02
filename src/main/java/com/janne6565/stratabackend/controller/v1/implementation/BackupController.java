package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.BackupApi;
import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.services.backup.BackupService;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements {@link BackupApi}. Both operations are gated to {@code OWNER} via {@link
 * NeedsValidation}. The download path-variable is validated inside {@link BackupService} against
 * the strict backup-name pattern, so it cannot escape the backup directory.
 */
@RestController
@RequiredArgsConstructor
public class BackupController implements BackupApi {

    private final BackupService backupService;

    @Override
    @NeedsValidation(Operation.BACKUP_LIST)
    public List<BackupResponse> list() {
        return backupService.listBackups();
    }

    @Override
    @NeedsValidation(Operation.BACKUP_DOWNLOAD)
    public ResponseEntity<Resource> download(String name) {
        Path file = backupService.requireBackupFile(name);
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .body(resource);
    }
}
