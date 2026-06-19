package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.action.ChangeRoleRequest;
import com.janne6565.stratabackend.model.action.CreateUserRequest;
import com.janne6565.stratabackend.model.core.UserResponse;
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
 * Admin user-management contract (API-first; see {@link AuthApi}). Authorization is enforced on the
 * controller implementation via {@code @NeedsValidation}, not here — Spring AOP only sees
 * annotations on the invoked (implementation) method.
 */
@Tag(name = "Users")
@RequestMapping(path = "/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
public interface UserApi {

    @Operation(summary = "List all users")
    @GetMapping
    List<UserResponse> list();

    @Operation(summary = "Create a local user account")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest request);

    @Operation(summary = "Change a user's role")
    @PatchMapping(path = "/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    UserResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request);

    @Operation(summary = "Delete a user")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id);
}
