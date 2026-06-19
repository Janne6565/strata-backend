package com.janne6565.stratabackend.grant.dto;

import com.janne6565.stratabackend.grant.AccessGrant;
import com.janne6565.stratabackend.grant.ScopeType;
import java.time.Instant;
import java.util.UUID;

/** Public view of an access grant. */
public record GrantResponse(
        UUID id,
        UUID userId,
        ScopeType scopeType,
        String namespace,
        UUID datasourceId,
        String datasourceName,
        boolean readOnly,
        Instant createdAt) {

    public static GrantResponse from(AccessGrant grant) {
        return new GrantResponse(
                grant.getId(),
                grant.getUser().getId(),
                grant.getScopeType(),
                grant.getNamespace(),
                grant.getDatasource() == null ? null : grant.getDatasource().getId(),
                grant.getDatasource() == null ? null : grant.getDatasource().getDisplayName(),
                grant.isReadOnly(),
                grant.getCreatedAt());
    }
}
