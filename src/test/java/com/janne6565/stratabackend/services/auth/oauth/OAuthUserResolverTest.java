package com.janne6565.stratabackend.services.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.configuration.oauth.OAuthProperties;
import com.janne6565.stratabackend.entity.OAuthIdentityEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.exception.OAuthAccessDeniedException;
import com.janne6565.stratabackend.repository.OAuthIdentityRepository;
import com.janne6565.stratabackend.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the transactional user resolution / creation / role sync logic. */
@ExtendWith(MockitoExtension.class)
class OAuthUserResolverTest {

    private static final String PROVIDER = "authentik";
    private static final String ADMIN_GROUP = "strata-admins";
    private static final String USER_GROUP = "strata-users";

    @Mock private OAuthIdentityRepository identityRepository;
    @Mock private UserRepository userRepository;

    private OAuthUserResolver resolver;

    @BeforeEach
    void setUp() {
        OAuthProperties properties =
                new OAuthProperties(Map.of(), new OAuthProperties.Groups(ADMIN_GROUP, USER_GROUP));
        resolver = new OAuthUserResolver(properties, identityRepository, userRepository);
    }

    private OAuthUserInfo userInfo(
            String subject, String username, String email, String... groups) {
        return new OAuthUserInfo(subject, email, username, List.of(groups));
    }

    // --- new user creation -------------------------------------------------

    @Test
    void resolveLoginUser_newUser_createsUserWithNullPasswordAndUserRole() {
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserEntity result =
                resolver.resolveLoginUser(
                        PROVIDER, userInfo("sub-1", "alice", "alice@example.com", USER_GROUP));

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRole()).isEqualTo(Role.USER);
        assertThat(result.getPasswordHash()).isNull();
        verify(identityRepository).save(any(OAuthIdentityEntity.class));
    }

    @Test
    void resolveLoginUser_adminGroup_createsAdmin() {
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-2"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername("boss")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserEntity result =
                resolver.resolveLoginUser(
                        PROVIDER, userInfo("sub-2", "boss", "boss@example.com", ADMIN_GROUP));

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void resolveLoginUser_usernameCollision_appendsRandomSuffix() {
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-3"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        when(userRepository.existsByUsername(argThat(s -> s != null && s.startsWith("alice-"))))
                .thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserEntity result =
                resolver.resolveLoginUser(
                        PROVIDER, userInfo("sub-3", "alice", "alice2@example.com", USER_GROUP));

        assertThat(result.getUsername()).matches("alice-[a-z0-9]{6}");
    }

    @Test
    void resolveLoginUser_blankUsername_fallsBackToEmailLocalPart() {
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-4"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername("jane")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserEntity result =
                resolver.resolveLoginUser(
                        PROVIDER, userInfo("sub-4", "", "jane@example.com", USER_GROUP));

        assertThat(result.getUsername()).isEqualTo("jane");
    }

    // --- strict group gate -------------------------------------------------

    @Test
    void resolveLoginUser_noStrataGroup_throwsAccessDeniedAndCreatesNothing() {
        assertThatThrownBy(
                        () ->
                                resolver.resolveLoginUser(
                                        PROVIDER,
                                        userInfo(
                                                "sub-5", "nobody", "x@example.com", "other-group")))
                .isInstanceOf(OAuthAccessDeniedException.class);

        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any());
    }

    // --- existing identity + role sync -------------------------------------

    @Test
    void resolveLoginUser_existingIdentity_syncsRoleUserToAdmin() {
        UserEntity user = new UserEntity("bob", null, Role.USER);
        OAuthIdentityEntity identity =
                new OAuthIdentityEntity(user, PROVIDER, "sub-6", "bob@example.com");
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-6"))
                .thenReturn(Optional.of(identity));

        UserEntity result =
                resolver.resolveLoginUser(
                        PROVIDER, userInfo("sub-6", "bob", "bob@example.com", ADMIN_GROUP));

        assertThat(result).isSameAs(user);
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any());
    }

    @Test
    void resolveLoginUser_existingIdentity_syncsRoleAdminToUser() {
        UserEntity user = new UserEntity("bob", null, Role.ADMIN);
        OAuthIdentityEntity identity =
                new OAuthIdentityEntity(user, PROVIDER, "sub-7", "bob@example.com");
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-7"))
                .thenReturn(Optional.of(identity));

        UserEntity result =
                resolver.resolveLoginUser(
                        PROVIDER, userInfo("sub-7", "bob", "bob@example.com", USER_GROUP));

        assertThat(result.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void resolveLoginUser_existingOwner_neverDowngraded() {
        UserEntity owner = new UserEntity("root", "hash", Role.OWNER);
        OAuthIdentityEntity identity =
                new OAuthIdentityEntity(owner, PROVIDER, "sub-8", "root@example.com");
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-8"))
                .thenReturn(Optional.of(identity));

        UserEntity result =
                resolver.resolveLoginUser(
                        PROVIDER, userInfo("sub-8", "root", "root@example.com", USER_GROUP));

        assertThat(result.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    void resolveLoginUser_disabledLinkedUser_throwsAccessDenied() {
        UserEntity user = new UserEntity("bob", null, Role.USER);
        user.setEnabled(false);
        OAuthIdentityEntity identity =
                new OAuthIdentityEntity(user, PROVIDER, "sub-9", "bob@example.com");
        when(identityRepository.findByProviderAndProviderSubject(PROVIDER, "sub-9"))
                .thenReturn(Optional.of(identity));

        assertThatThrownBy(
                        () ->
                                resolver.resolveLoginUser(
                                        PROVIDER,
                                        userInfo("sub-9", "bob", "bob@example.com", USER_GROUP)))
                .isInstanceOf(OAuthAccessDeniedException.class);
    }
}
