package com.janne6565.stratabackend.controller.v1.implementation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.services.backup.DatasourceBackupService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DatasourceBackupControllerTest {

    private static final UUID DS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BACKUP_NAME = "strata-backup-20260703T140000Z.dump";

    @Mock private DatasourceBackupService datasourceBackupService;
    @TempDir private Path directory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new DatasourceBackupController(datasourceBackupService))
                        .build();
    }

    @Test
    void listReturnsBackupMetadataForTheDatasource() throws Exception {
        when(datasourceBackupService.listBackups(DS_ID))
                .thenReturn(
                        List.of(
                                new BackupResponse(
                                        BACKUP_NAME, 4096, Instant.parse("2026-07-03T14:00:00Z"))));

        mockMvc.perform(get("/v1/datasources/{id}/backups", DS_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(BACKUP_NAME))
                .andExpect(jsonPath("$[0].sizeBytes").value(4096));
    }

    @Test
    void downloadStreamsTheFileAsAnAttachment() throws Exception {
        Path file = directory.resolve(BACKUP_NAME);
        Files.write(file, new byte[] {'P', 'G', 'D', 'M', 'P'});
        when(datasourceBackupService.requireBackupFile(DS_ID, BACKUP_NAME)).thenReturn(file);

        mockMvc.perform(get("/v1/datasources/{id}/backups/{name}/download", DS_ID, BACKUP_NAME))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + BACKUP_NAME + "\""))
                .andExpect(content().bytes(new byte[] {'P', 'G', 'D', 'M', 'P'}));
    }
}
