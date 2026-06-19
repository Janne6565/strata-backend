package com.janne6565.stratabackend.catalog;

import java.util.List;

/**
 * The persisted description of how a datasource's credentials resolve from the cluster — the
 * strategy plus a per-field source map. Stored in {@code datasource.credential_resolution} (jsonb).
 * Holds pointers (secret/key), never secret values.
 */
public record CredentialResolution(String strategy, List<CredentialSource> sources) {

    public boolean isFullyResolved() {
        return sources.stream().noneMatch(s -> s.type() == CredentialSourceType.UNRESOLVED);
    }
}
