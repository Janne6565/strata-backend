package com.janne6565.stratabackend.services.backup;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tiered ("grandfather-father-son") retention. The configured tiers form age buckets {@code (0,T1],
 * (T1,T2], ...}; within each bucket the <em>oldest</em> backup survives, so a survivor eventually
 * crosses the next boundary and takes over that tier — a bucket is never left empty while data that
 * old exists. Everything older than the last tier is deleted; the newest backup overall is kept
 * regardless of age unless the caller opts out. Pure function of its inputs (no I/O, no wall
 * clock).
 */
@Component
public class BackupRetentionPolicy {

    /** A backup file and its creation time as parsed from the file name. */
    public record StoredBackup(Path path, Instant createdAt) {}

    private final List<Duration> tiers;

    public BackupRetentionPolicy(BackupProperties properties) {
        this.tiers = List.copyOf(properties.retentionTiers());
    }

    /** Tiered retention with the safety net on: the newest backup is never deleted. */
    public List<StoredBackup> selectDeletions(List<StoredBackup> all, Instant now) {
        return selectDeletions(all, now, true);
    }

    /**
     * Tiered retention. With {@code keepNewest} the most recent backup always survives, so a live
     * datasource is never left without a restore point however old its last dump is. Orphaned
     * directories (the datasource was deleted) pass {@code false}: nothing needs a restore point
     * any more, so the tiers alone decide and the directory drains to empty once its last dump
     * crosses the final tier.
     */
    public List<StoredBackup> selectDeletions(
            List<StoredBackup> all, Instant now, boolean keepNewest) {
        if (all.isEmpty()) {
            return List.of();
        }
        Set<StoredBackup> keep = new HashSet<>();
        if (keepNewest) {
            keep.add(all.stream().max(Comparator.comparing(StoredBackup::createdAt)).orElseThrow());
        }

        Duration lowerBound = Duration.ZERO;
        for (Duration upperBound : tiers) {
            Duration lower = lowerBound;
            all.stream()
                    .filter(
                            backup -> {
                                Duration age = age(backup, now);
                                return age.compareTo(lower) > 0 && age.compareTo(upperBound) <= 0;
                            })
                    .min(Comparator.comparing(StoredBackup::createdAt))
                    .ifPresent(keep::add);
            lowerBound = upperBound;
        }

        List<StoredBackup> deletions = new ArrayList<>(all);
        deletions.removeAll(keep);
        return deletions;
    }

    private static Duration age(StoredBackup backup, Instant now) {
        Duration age = Duration.between(backup.createdAt(), now);
        return age.isNegative() ? Duration.ZERO : age;
    }
}
