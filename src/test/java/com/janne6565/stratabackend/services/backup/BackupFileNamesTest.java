package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BackupFileNamesTest {

    @Test
    void formatAndParseRoundTrip() {
        Instant createdAt = Instant.parse("2026-07-02T14:00:00Z");

        String fileName = BackupFileNames.fileName(createdAt);

        assertThat(fileName).isEqualTo("strata-backup-20260702T140000Z.dump");
        assertThat(BackupFileNames.parse(fileName)).contains(createdAt);
    }

    @Test
    void parseRejectsForeignNames() {
        assertThat(BackupFileNames.parse("lost+found")).isEmpty();
        assertThat(BackupFileNames.parse("strata-backup-notatimestamp.dump")).isEmpty();
        assertThat(BackupFileNames.parse("strata-backup-20260702T140000Z.dump.tmp")).isEmpty();
        assertThat(BackupFileNames.parse("other-20260702T140000Z.dump")).isEmpty();
        assertThat(BackupFileNames.parse("strata-backup-20260702T140000Z.json")).isEmpty();
    }
}
