package com.janne6565.stratabackend.configuration.backup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Scheduled database backup configuration. Bound from {@code strata.backup}. The retention tiers
 * are bucket boundaries (strictly increasing); backups older than the last tier are pruned.
 */
@Validated
@ConfigurationProperties("strata.backup")
public record BackupProperties(
        boolean enabled,
        @NotBlank String directory,
        @NotBlank String cron,
        @NotEmpty List<Duration> retentionTiers,
        @NotBlank String pgDumpPath,
        @NotNull Duration timeout) {

    public BackupProperties {
        if (retentionTiers != null) {
            for (int i = 1; i < retentionTiers.size(); i++) {
                if (retentionTiers.get(i).compareTo(retentionTiers.get(i - 1)) <= 0) {
                    throw new IllegalArgumentException(
                            "strata.backup.retention-tiers must be strictly increasing");
                }
            }
        }
    }
}
