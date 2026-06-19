package com.janne6565.stratabackend.services.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.entity.AccessGrantEntity;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.CreateGrantRequest;
import com.janne6565.stratabackend.model.core.GrantResponse;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.core.ScopeType;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.AccessGrantRepository;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantServiceTest {

    @Mock private AccessGrantRepository grantRepository;
    @Mock private UserRepository userRepository;
    @Mock private DatasourceRepository datasourceRepository;
    @InjectMocks private GrantService grantService;

    private final UserEntity caller = new UserEntity("admin", "h", Role.ADMIN);

    @Test
    void createsNamespaceGrant() {
        UserEntity target = new UserEntity("u", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(grantRepository.save(any(AccessGrantEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GrantResponse response =
                grantService.create(
                        new CreateGrantRequest(
                                target.getId(), ScopeType.NAMESPACE, "prod", null, true),
                        caller);

        assertThat(response.scopeType()).isEqualTo(ScopeType.NAMESPACE);
        assertThat(response.namespace()).isEqualTo("prod");
        assertThat(response.datasourceId()).isNull();
        assertThat(response.readOnly()).isTrue();
    }

    @Test
    void namespaceGrantRequiresNamespace() {
        UserEntity target = new UserEntity("u", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                target.getId(),
                                                ScopeType.NAMESPACE,
                                                " ",
                                                null,
                                                false),
                                        caller))
                .isInstanceOf(BadRequestException.class);
        verify(grantRepository, never()).save(any());
    }

    @Test
    void namespaceGrantRejectsDatasource() {
        UserEntity target = new UserEntity("u", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                target.getId(),
                                                ScopeType.NAMESPACE,
                                                "prod",
                                                UUID.randomUUID(),
                                                false),
                                        caller))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createsDatabaseGrant() {
        UserEntity target = new UserEntity("u", "h", Role.USER);
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(UUID.randomUUID());
        ds.setDisplayName("orders-db");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(datasourceRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(grantRepository.save(any(AccessGrantEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GrantResponse response =
                grantService.create(
                        new CreateGrantRequest(
                                target.getId(), ScopeType.DATABASE, null, ds.getId(), false),
                        caller);

        assertThat(response.scopeType()).isEqualTo(ScopeType.DATABASE);
        assertThat(response.datasourceId()).isEqualTo(ds.getId());
        assertThat(response.datasourceName()).isEqualTo("orders-db");
    }

    @Test
    void databaseGrantRequiresDatasourceId() {
        UserEntity target = new UserEntity("u", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                target.getId(),
                                                ScopeType.DATABASE,
                                                null,
                                                null,
                                                false),
                                        caller))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void databaseGrantWithUnknownDatasource404s() {
        UserEntity target = new UserEntity("u", "h", Role.USER);
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(datasourceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                target.getId(),
                                                ScopeType.DATABASE,
                                                null,
                                                missing,
                                                false),
                                        caller))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createWithUnknownUser404s() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                missing, ScopeType.NAMESPACE, "prod", null, false),
                                        caller))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokeUnknownGrant404s() {
        UUID missing = UUID.randomUUID();
        when(grantRepository.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> grantService.revoke(missing))
                .isInstanceOf(NotFoundException.class);
        verify(grantRepository, never()).deleteById(any());
    }

    @Test
    void revokeExistingGrantDeletesIt() {
        UUID id = UUID.randomUUID();
        when(grantRepository.existsById(id)).thenReturn(true);

        grantService.revoke(id);

        verify(grantRepository).deleteById(id);
    }
}
