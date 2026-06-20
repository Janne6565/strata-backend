package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.action.ManualAddRequest;
import com.janne6565.stratabackend.model.action.RenameRequest;
import com.janne6565.stratabackend.model.core.DatasourceResponse;
import com.janne6565.stratabackend.model.core.DiscoverySummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * DatasourceEntity inventory + discovery contract. Authorization enforced on the implementation.
 */
@Tag(name = "Inventory")
@RequestMapping(path = "/v1/datasources", produces = MediaType.APPLICATION_JSON_VALUE)
public interface InventoryApi {

    @Operation(summary = "List all known datasources (discovered + manual)")
    @GetMapping
    List<DatasourceResponse> list();

    @Operation(summary = "Get a single datasource")
    @GetMapping("/{id}")
    DatasourceResponse get(@PathVariable UUID id);

    @Operation(summary = "Rescan the cluster and reconcile the catalog")
    @PostMapping("/rescan")
    DiscoverySummary rescan();

    @Operation(summary = "Manually register a datasource")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DatasourceResponse manualAdd(@Valid @RequestBody ManualAddRequest request);

    @Operation(summary = "Rename a datasource (set its display name)")
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    DatasourceResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request);

    @Operation(summary = "Remove a datasource from the catalog")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@PathVariable UUID id);
}
