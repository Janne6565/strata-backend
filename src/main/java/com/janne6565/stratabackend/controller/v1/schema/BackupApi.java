package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.core.BackupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Database-backup contract (API-first). Backups are the most sensitive artifact in the system, so
 * every operation is OWNER-gated on the implementation via {@code @NeedsValidation} — Spring AOP
 * only sees annotations on the invoked (implementation) method, not here.
 */
@Tag(name = "Backups")
@RequestMapping("/v1/backups")
public interface BackupApi {

    @Operation(summary = "List available database backups")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    List<BackupResponse> list();

    @Operation(summary = "Download a database backup by file name")
    @GetMapping(path = "/{name}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<Resource> download(@PathVariable String name);
}
