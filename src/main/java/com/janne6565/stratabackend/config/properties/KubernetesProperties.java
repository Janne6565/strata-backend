package com.janne6565.stratabackend.config.properties;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Cluster scanning scope and prod safe-mode. Bound from {@code strata.kubernetes}. */
@Validated
@ConfigurationProperties("strata.kubernetes")
public record KubernetesProperties(
        @NotNull NamespaceScope namespaceScope,
        List<String> prodNamespacePatterns,
        boolean readOnlySafeMode) {

    public enum NamespaceScope {
        ALL
    }
}
