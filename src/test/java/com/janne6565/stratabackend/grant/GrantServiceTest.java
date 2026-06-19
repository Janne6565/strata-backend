package com.janne6565.stratabackend.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.auth.UserRepository;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.BadRequestException;
import com.janne6565.stratabackend.common.NotFoundException;
import com.janne6565.stratabackend.grant.dto.CreateGrantRequest;
import com.janne6565.stratabackend.grant.dto.GrantResponse;
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

    private final User caller = new User("admin", "h", Role.ADMIN);

    @Test
    void createsNamespaceGrant() {
        User target = new User("u", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(grantRepository.save(any(AccessGrant.class))).thenAnswer(inv -> inv.getArgument(0));

        GrantResponse response =
                grantService.create(
                        new CreateGrantRequest(target.getId(), ScopeType.NAMESPACE, "prod", null, true),
                        caller);

        assertThat(response.scopeType()).isEqualTo(ScopeType.NAMESPACE);
        assertThat(response.namespace()).isEqualTo("prod");
        assertThat(response.datasourceId()).isNull();
        assertThat(response.readOnly()).isTrue();
    }

    @Test
    void namespaceGrantRequiresNamespace() {
        User target = new User("u", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                target.getId(), ScopeType.NAMESPACE, " ", null, false),
                                        caller))
                .isInstanceOf(BadRequestException.class);
        verify(grantRepository, never()).save(any());
    }

    @Test
    void namespaceGrantRejectsDatasource() {
        User target = new User("u", "h", Role.USER);
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
        User target = new User("u", "h", Role.USER);
        Datasource ds = new Datasource();
        ds.setId(UUID.randomUUID());
        ds.setDisplayName("orders-db");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(datasourceRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(grantRepository.save(any(AccessGrant.class))).thenAnswer(inv -> inv.getArgument(0));

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
        User target = new User("u", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                target.getId(), ScopeType.DATABASE, null, null, false),
                                        caller))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void databaseGrantWithUnknownDatasource404s() {
        User target = new User("u", "h", Role.USER);
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(datasourceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                grantService.create(
                                        new CreateGrantRequest(
                                                target.getId(), ScopeType.DATABASE, null, missing, false),
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

        assertThatThrownBy(() -> grantService.revoke(missing)).isInstanceOf(NotFoundException.class);
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
