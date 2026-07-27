package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.services.backup.BackupRetentionPolicy.StoredBackup;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackupRetentionPolicyTest {

    private static final List<Duration> TIERS =
            List.of(
                    Duration.ofHours(2),
                    Duration.ofDays(1),
                    Duration.ofDays(3),
                    Duration.ofDays(7),
                    Duration.ofDays(14));
    private static final Instant NOW = Instant.parse("2026-07-02T14:00:00Z");

    private final BackupRetentionPolicy policy = new BackupRetentionPolicy(properties(TIERS));

    @Test
    void emptyInputDeletesNothing() {
        assertThat(policy.selectDeletions(List.of(), NOW)).isEmpty();
    }

    @Test
    void newestIsAlwaysKeptEvenWithSiblingsInTheFirstBucket() {
        StoredBackup newest = backupAged(Duration.ofMinutes(5));
        StoredBackup middle = backupAged(Duration.ofMinutes(30));
        StoredBackup oldest = backupAged(Duration.ofMinutes(90));

        List<StoredBackup> deletions = policy.selectDeletions(List.of(newest, middle, oldest), NOW);

        // Newest survives unconditionally, oldest survives as the first bucket's keeper.
        assertThat(deletions).containsExactly(middle);
    }

    @Test
    void oldestPerBucketIsKept() {
        StoredBackup fresh = backupAged(Duration.ofHours(1));
        StoredBackup dayYoung = backupAged(Duration.ofHours(5));
        StoredBackup dayOld = backupAged(Duration.ofHours(20));
        StoredBackup weekYoung = backupAged(Duration.ofDays(4));
        StoredBackup weekOld = backupAged(Duration.ofDays(6));

        List<StoredBackup> deletions =
                policy.selectDeletions(List.of(fresh, dayYoung, dayOld, weekYoung, weekOld), NOW);

        assertThat(deletions).containsExactlyInAnyOrder(dayYoung, weekYoung);
    }

    @Test
    void backupsOlderThanTheLastTierAreDeleted() {
        StoredBackup fresh = backupAged(Duration.ofHours(1));
        StoredBackup expired = backupAged(Duration.ofDays(15));
        StoredBackup ancient = backupAged(Duration.ofDays(60));

        List<StoredBackup> deletions =
                policy.selectDeletions(List.of(fresh, expired, ancient), NOW);

        assertThat(deletions).containsExactlyInAnyOrder(expired, ancient);
    }

    @Test
    void withoutTheNewestSafetyNetAnOldSoleBackupIsDeleted() {
        // An orphaned directory whose only remaining dump is past the last tier: with the safety
        // net it would be kept forever, without it the directory can finally drain.
        StoredBackup soleAncient = backupAged(Duration.ofDays(90));

        assertThat(policy.selectDeletions(List.of(soleAncient), NOW)).isEmpty();
        assertThat(policy.selectDeletions(List.of(soleAncient), NOW, false))
                .containsExactly(soleAncient);
    }

    @Test
    void withoutTheNewestSafetyNetInTierBackupsStillSurvive() {
        // Dropping the net must not turn prune into "delete everything" — a still-orphaned
        // directory keeps its in-tier history and only drains as that history ages out.
        StoredBackup recent = backupAged(Duration.ofMinutes(10));
        StoredBackup week = backupAged(Duration.ofDays(6));
        StoredBackup expired = backupAged(Duration.ofDays(20));

        List<StoredBackup> deletions =
                policy.selectDeletions(List.of(recent, week, expired), NOW, false);

        assertThat(deletions).containsExactly(expired);
    }

    @Test
    void boundaryExactAgeBelongsToTheLowerBucket() {
        // Buckets are (lower, upper]: an exactly-2h-old backup is still in the first bucket.
        StoredBackup atBoundary = backupAged(Duration.ofHours(2));
        StoredBackup justOver = backupAged(Duration.ofHours(2).plusSeconds(1));

        List<StoredBackup> deletions = policy.selectDeletions(List.of(atBoundary, justOver), NOW);

        // atBoundary: newest + first-bucket keeper; justOver: second bucket's keeper.
        assertThat(deletions).isEmpty();
    }

    @Test
    void futureTimestampsAreTreatedAsAgeZero() {
        StoredBackup future = backupAged(Duration.ofHours(-1));
        StoredBackup present = backupAged(Duration.ofMinutes(10));

        List<StoredBackup> deletions = policy.selectDeletions(List.of(future, present), NOW);

        // future is the newest, present is the first bucket's oldest — both survive.
        assertThat(deletions).isEmpty();
    }

    @Test
    void agingSimulationRetainsABoundedSpreadOfHistory() {
        // A backup every 2h for 30 days, pruning after each run. This exercises the promotion
        // dynamics: a bucket's keeper ages across boundaries and takes over the next tier.
        List<StoredBackup> retained = new ArrayList<>();
        Instant clock = NOW;
        for (int run = 0; run < 30 * 12; run++) {
            clock = clock.plus(Duration.ofHours(2));
            StoredBackup fresh = new StoredBackup(Path.of("run-" + run), clock);
            retained.add(fresh);
            retained.removeAll(policy.selectDeletions(List.copyOf(retained), clock));

            // Core invariants at every step: never exceed the cap, and never lose the newest.
            assertThat(retained).hasSizeLessThanOrEqualTo(TIERS.size() + 1).contains(fresh);
        }

        // Steady state keeps a healthy spread, not a collapsed one or two files. (Individual
        // middle tiers can be transiently empty while a keeper is between boundaries — that is
        // expected; what matters is bounded count plus real long-term coverage below.)
        Instant end = clock;
        assertThat(retained).hasSizeGreaterThanOrEqualTo(TIERS.size() - 1);

        // The fortnight tier works: we still hold a backup older than a week (the "~2 weeks ago"
        // snapshot the retention is meant to preserve), and nothing older than the last tier.
        Duration oldestAge =
                Duration.between(
                        retained.stream()
                                .map(StoredBackup::createdAt)
                                .min(Instant::compareTo)
                                .orElseThrow(),
                        end);
        assertThat(oldestAge)
                .isGreaterThan(Duration.ofDays(7))
                .isLessThanOrEqualTo(Duration.ofDays(14));
    }

    private static StoredBackup backupAged(Duration age) {
        Instant createdAt = NOW.minus(age);
        return new StoredBackup(Path.of(BackupFileNames.fileName(createdAt)), createdAt);
    }

    private static BackupProperties properties(List<Duration> tiers) {
        return new BackupProperties(
                false, "/backups", "0 0 */2 * * *", tiers, "pg_dump", Duration.ofMinutes(5));
    }
}
