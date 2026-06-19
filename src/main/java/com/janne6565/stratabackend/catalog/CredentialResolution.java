package com.janne6565.stratabackend.catalog;

import java.util.List;

/**
 * The persisted description of how a datasource's credentials resolve from the cluster — the
 * strategy plus a per-field source map. Stored in {@code datasource.credential_resolution} (jsonb).
 * Holds pointers (secret/key), never secret values.
 */
public record CredentialResolution(String strategy, List<CredentialSource> sources) {

    /**
     * Named without an {@code is}/{@code get} prefix on purpose: it is a derived helper, not a
     * bean property, so the JSON mapper does not serialize it into the persisted jsonb.
     */
    public boolean allResolved() {
        return sources.stream().noneMatch(s -> s.type() == CredentialSourceType.UNRESOLVED);
    }
}
