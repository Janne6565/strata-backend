package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupSchedulerTest {

    @Mock private BackupService backupService;
    @InjectMocks private BackupScheduler backupScheduler;

    @TempDir private Path directory;

    @Test
    void failedBackupStillPrunesAndThrowsNothing() throws IOException {
        when(backupService.writeBackup()).thenThrow(new BackupException("boom"));

        assertThatCode(() -> backupScheduler.run()).doesNotThrowAnyException();
        verify(backupService).prune();
    }

    @Test
    void failedPruneThrowsNothing() throws IOException {
        Path backup = Files.writeString(directory.resolve("backup.dump"), "PGDMP");
        when(backupService.writeBackup()).thenReturn(backup);
        when(backupService.prune()).thenThrow(new IOException("disk gone"));

        assertThatCode(() -> backupScheduler.run()).doesNotThrowAnyException();
    }
}
