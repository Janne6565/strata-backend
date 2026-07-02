package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.core.BackupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Per-datasource backup contract (API-first). Listing and downloading a datasource's backups
 * require read access to that datasource, enforced on the implementation via
 * {@code @NeedsValidation} — Spring AOP only sees annotations on the invoked method, not here.
 */
@Tag(name = "Datasource Backups")
@RequestMapping("/v1/datasources/{id}/backups")
public interface DatasourceBackupApi {

    @Operation(summary = "List available backups for a datasource")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    List<BackupResponse> list(@PathVariable UUID id);

    @Operation(summary = "Download a datasource backup by file name")
    @GetMapping(path = "/{name}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<Resource> download(@PathVariable UUID id, @PathVariable String name);
}
