package com.janne6565.stratabackend.controller.v1.implementation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.services.backup.BackupService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone web-layer test for the file-serving behavior: routing of the dotted backup file name,
 * byte streaming, and the download headers. Authorization (the {@code @NeedsValidation} aspect) is
 * out of scope here and covered by the policy unit tests.
 */
@ExtendWith(MockitoExtension.class)
class BackupControllerTest {

    private static final String BACKUP_NAME = "strata-backup-20260702T140000Z.dump";

    @Mock private BackupService backupService;
    @TempDir private Path directory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BackupController(backupService)).build();
    }

    @Test
    void listReturnsBackupMetadata() throws Exception {
        when(backupService.listBackups())
                .thenReturn(
                        List.of(
                                new BackupResponse(
                                        BACKUP_NAME, 2048, Instant.parse("2026-07-02T14:00:00Z"))));

        mockMvc.perform(get("/v1/backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(BACKUP_NAME))
                .andExpect(jsonPath("$[0].sizeBytes").value(2048));
    }

    @Test
    void downloadStreamsTheFileAsAnAttachment() throws Exception {
        Path file = directory.resolve(BACKUP_NAME);
        Files.write(file, new byte[] {'P', 'G', 'D', 'M', 'P'});
        when(backupService.requireBackupFile(BACKUP_NAME)).thenReturn(file);

        mockMvc.perform(get("/v1/backups/{name}/download", BACKUP_NAME))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + BACKUP_NAME + "\""))
                .andExpect(content().bytes(new byte[] {'P', 'G', 'D', 'M', 'P'}));
    }
}
