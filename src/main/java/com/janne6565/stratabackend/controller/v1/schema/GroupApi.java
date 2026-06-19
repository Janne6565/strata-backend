package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.action.AddMemberRequest;
import com.janne6565.stratabackend.model.action.CreateGroupRequest;
import com.janne6565.stratabackend.model.action.ReorderGroupsRequest;
import com.janne6565.stratabackend.model.action.UpdateGroupRequest;
import com.janne6565.stratabackend.model.core.GroupResponse;
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
 * Group-management contract. Groups are private to their owner; authorization (ownership) is
 * enforced on the controller implementation via the policy aspect.
 */
@Tag(name = "Groups")
@RequestMapping(path = "/v1/groups", produces = MediaType.APPLICATION_JSON_VALUE)
public interface GroupApi {

    @Operation(summary = "List the caller's datasource groups")
    @GetMapping
    List<GroupResponse> list();

    @Operation(summary = "Create a datasource group")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse create(@Valid @RequestBody CreateGroupRequest request);

    @Operation(summary = "Rename a group")
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    GroupResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateGroupRequest request);

    @Operation(summary = "Reorder the caller's groups")
    @PostMapping(path = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorder(@Valid @RequestBody ReorderGroupsRequest request);

    @Operation(summary = "Delete a group")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id);

    @Operation(summary = "Add a datasource to a group")
    @PostMapping(path = "/{id}/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    GroupResponse addMember(@PathVariable UUID id, @Valid @RequestBody AddMemberRequest request);

    @Operation(summary = "Remove a datasource from a group")
    @DeleteMapping("/{id}/members/{datasourceId}")
    GroupResponse removeMember(@PathVariable UUID id, @PathVariable UUID datasourceId);
}
