package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.action.CreateGrantRequest;
import com.janne6565.stratabackend.model.core.GrantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Admin grant-management contract. Authorization is enforced on the controller implementation. */
@Tag(name = "Grants")
@RequestMapping(path = "/api/grants", produces = MediaType.APPLICATION_JSON_VALUE)
public interface GrantApi {

    @Operation(summary = "List a user's access grants")
    @GetMapping
    List<GrantResponse> listForUser(@RequestParam UUID userId);

    @Operation(summary = "Grant a user access to a namespace or datasource")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    GrantResponse create(@Valid @RequestBody CreateGrantRequest request);

    @Operation(summary = "Revoke an access grant")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id);
}
