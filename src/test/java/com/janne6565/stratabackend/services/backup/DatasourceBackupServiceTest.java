package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.configuration.backup.BackupProperties;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.BackupResponse;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.engine.ConnectionDetailsResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasourceBackupServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-03T14:00:00Z");

    @TempDir private Path backupRoot;

    private DatasourceRepository datasourceRepository;
    private ConnectionDetailsResolver connectionResolver;
    private PgDumpRunner pgDumpRunner;
    private DatasourceBackupService service;

    @BeforeEach
    void setUp() {
        datasourceRepository = mock(DatasourceRepository.class);
        connectionResolver = mock(ConnectionDetailsResolver.class);
        pgDumpRunner = mock(PgDumpRunner.class);
        BackupProperties properties =
                new BackupProperties(
                        false,
                        backupRoot.toString(),
                        "0 0 */2 * * *",
                        List.of(Duration.ofHours(2), Duration.ofDays(14)),
                        "pg_dump",
                        Duration.ofMinutes(5));
        BackupStore store =
                new BackupStore(
                        new BackupRetentionPolicy(properties), Clock.fixed(NOW, ZoneOffset.UTC));
        service =
                new DatasourceBackupService(
                        datasourceRepository, connectionResolver, pgDumpRunner, store, properties);
        // The dump "runs" by writing placeholder bytes to the temp file it is given.
        doAnswer(
                        invocation -> {
                            Files.writeString(invocation.getArgument(0), "PGDMP");
                            return null;
                        })
                .when(pgDumpRunner)
                .dumpTo(any(), any());
    }

    @Test
    void backsUpOnlyPostgresDatasources() {
        DatasourceEntity postgres = datasource("postgresql");
        DatasourceEntity mysql = datasource("mysql");
        when(datasourceRepository.findAll()).thenReturn(List.of(postgres, mysql));
        when(connectionResolver.resolve(postgres)).thenReturn(details());

        service.backupAll();

        verify(connectionResolver).resolve(postgres);
        verify(connectionResolver, never()).resolve(mysql);
        assertThat(directoryFor(postgres))
                .isDirectoryContaining(p -> p.toString().endsWith(".dump"));
        assertThat(directoryFor(mysql)).doesNotExist();
    }

    @Test
    void skipsDatasourcesWithUnresolvableCredentialsAndContinues() {
        DatasourceEntity unresolved = datasource("postgresql");
        DatasourceEntity healthy = datasource("postgresql");
        when(datasourceRepository.findAll()).thenReturn(List.of(unresolved, healthy));
        doThrow(new BadRequestException("no backing service"))
                .when(connectionResolver)
                .resolve(unresolved);
        when(connectionResolver.resolve(healthy)).thenReturn(details());

        assertThatCode(() -> service.backupAll()).doesNotThrowAnyException();

        assertThat(directoryFor(unresolved)).doesNotExist();
        assertThat(directoryFor(healthy))
                .isDirectoryContaining(p -> p.toString().endsWith(".dump"));
    }

    @Test
    void listAndRequireResolveWithinTheDatasourceDirectory() {
        DatasourceEntity postgres = datasource("postgresql");
        when(datasourceRepository.findAll()).thenReturn(List.of(postgres));
        when(connectionResolver.resolve(postgres)).thenReturn(details());
        service.backupAll();

        var backups = service.listBackups(postgres.getId());
        assertThat(backups).hasSize(1);
        String name = backups.get(0).name();

        assertThat(service.requireBackupFile(postgres.getId(), name))
                .isEqualTo(directoryFor(postgres).resolve(name));
    }

    @Test
    void dumpTargetsTheResolvedConnection() {
        DatasourceEntity postgres = datasource("postgresql");
        ConnectionDetails details = details();
        when(datasourceRepository.findAll()).thenReturn(List.of(postgres));
        when(connectionResolver.resolve(postgres)).thenReturn(details);

        service.backupAll();

        verify(pgDumpRunner).dumpTo(any(Path.class), eq(details));
    }

    @Test
    void backupNowWritesAndReturnsTheNewBackupMetadata() {
        DatasourceEntity postgres = datasource("postgresql");
        when(datasourceRepository.findById(postgres.getId())).thenReturn(Optional.of(postgres));
        when(connectionResolver.resolve(postgres)).thenReturn(details());

        BackupResponse created = service.backupNow(postgres.getId());

        assertThat(created.name()).endsWith(".dump");
        assertThat(directoryFor(postgres).resolve(created.name())).exists();
    }

    @Test
    void backupNow404sForAnUnknownDatasource() {
        UUID unknown = UUID.randomUUID();
        when(datasourceRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.backupNow(unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void backupNowRejectsNonPostgresDatasources() {
        DatasourceEntity mysql = datasource("mysql");
        when(datasourceRepository.findById(mysql.getId())).thenReturn(Optional.of(mysql));

        assertThatThrownBy(() -> service.backupNow(mysql.getId()))
                .isInstanceOf(BadRequestException.class);
        verify(connectionResolver, never()).resolve(mysql);
    }

    @Test
    void backupNowSurfacesUnresolvableCredentials() {
        DatasourceEntity postgres = datasource("postgresql");
        when(datasourceRepository.findById(postgres.getId())).thenReturn(Optional.of(postgres));
        doThrow(new BadRequestException("no backing service"))
                .when(connectionResolver)
                .resolve(postgres);

        assertThatThrownBy(() -> service.backupNow(postgres.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    private Path directoryFor(DatasourceEntity ds) {
        return backupRoot.resolve("datasources").resolve(ds.getId().toString());
    }

    private static ConnectionDetails details() {
        return new ConnectionDetails("postgresql", "db.host", 5432, "app", "user", "secret");
    }

    private static DatasourceEntity datasource(String driver) {
        DatasourceEntity ds = mock(DatasourceEntity.class);
        when(ds.getId()).thenReturn(UUID.randomUUID());
        when(ds.getDriver()).thenReturn(driver);
        return ds;
    }
}
