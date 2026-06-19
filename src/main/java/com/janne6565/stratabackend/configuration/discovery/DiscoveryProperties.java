package com.janne6565.stratabackend.configuration.discovery;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auto-discovery detector rules (see ARCHITECTURE.md §8). Bound from {@code strata.discovery}. A
 * driver declares which credential fields it needs; a detector maps an image to a driver and names
 * where each field's value comes from (env var, or an explicit secret + key).
 */
@Validated
@ConfigurationProperties("strata.discovery")
public record DiscoveryProperties(List<Detector> detectors) {

    public record Detector(
            @NotBlank String id, @NotBlank String driver, Match match, Credentials credentials) {}

    /** Regex over the full {@code repository:tag} reference, plus optional corroborating ports. */
    public record Match(String ref, List<Integer> ports) {}

    public record Credentials(String strategy, Map<String, String> env, SecretRef secret) {}

    public record SecretRef(String name, Map<String, String> keys) {}
}
