package com.janne6565.stratabackend.grant.dto;

import com.janne6565.stratabackend.grant.ScopeType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to grant a user access to a scope. For {@code NAMESPACE} grants supply {@code namespace};
 * for {@code DATABASE} grants supply {@code datasourceId}. Scope consistency is validated server-side.
 */
public record CreateGrantRequest(
        @NotNull UUID userId,
        @NotNull ScopeType scopeType,
        String namespace,
        UUID datasourceId,
        boolean readOnly) {}
