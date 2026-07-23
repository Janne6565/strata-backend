package com.janne6565.stratabackend.services.auth.oauth;

import com.janne6565.stratabackend.configuration.oauth.OAuthProperties;
import com.janne6565.stratabackend.entity.OAuthIdentityEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.exception.OAuthAccessDeniedException;
import com.janne6565.stratabackend.repository.OAuthIdentityRepository;
import com.janne6565.stratabackend.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a provider identity to a local {@link UserEntity} in a single transaction: enforces the
 * strict group gate, reuses a linked identity or always creates a new user (Cosy behaviour), and
 * syncs the coarse role from the provider's groups ({@code OWNER} is never downgraded).
 *
 * <p>Deliberately a separate bean from {@link OAuthService}: the resolution must run inside a
 * transaction (dirty-checking role sync, atomic user+identity insert), but {@code
 * OAuthService.handleLoginCallback} wraps two remote HTTP calls and must not hold a DB connection
 * across them. Calling this across the bean boundary is what lets the {@code @Transactional} proxy
 * apply (self-invocation would silently bypass it — SPRING_BOOT.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthUserResolver {

    private static final String USERNAME_SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int USERNAME_SUFFIX_LENGTH = 6;
    private static final String FALLBACK_USERNAME = "user";

    private final SecureRandom random = new SecureRandom();

    private final OAuthProperties properties;
    private final OAuthIdentityRepository identityRepository;
    private final UserRepository userRepository;

    /**
     * Enforces the strict group gate first, then reuses a linked identity or always creates a new
     * user, and syncs the role. Runs in a single transaction so role sync flushes via dirty
     * checking and the user+identity insert is atomic.
     */
    @Transactional
    public UserEntity resolveLoginUser(String provider, OAuthUserInfo userInfo) {
        // Strict group gate BEFORE any create/login: throws when no strata-* group is present.
        Role mappedRole = mapRole(userInfo.groups());

        Optional<OAuthIdentityEntity> existing =
                identityRepository.findByProviderAndProviderSubject(provider, userInfo.subject());
        if (existing.isPresent()) {
            UserEntity user = existing.get().getUser();
            if (!user.isEnabled()) {
                throw new OAuthAccessDeniedException("Account is disabled");
            }
            syncRole(user, mappedRole);
            return user;
        }

        UserEntity user = new UserEntity(uniqueUsername(userInfo), null, mappedRole);
        userRepository.save(user);
        identityRepository.save(
                new OAuthIdentityEntity(user, provider, userInfo.subject(), userInfo.email()));
        log.info(
                "Created OAuth user '{}' via {} with role {}",
                user.getUsername(),
                provider,
                mappedRole);
        return user;
    }

    /** ADMIN when the admin group is present, else USER; rejects users in neither group. */
    private Role mapRole(List<String> groups) {
        boolean admin = groups.contains(properties.groups().admin());
        boolean user = groups.contains(properties.groups().user());
        if (!admin && !user) {
            throw new OAuthAccessDeniedException("No Strata group assigned");
        }
        return admin ? Role.ADMIN : Role.USER;
    }

    private void syncRole(UserEntity user, Role mappedRole) {
        if (user.getRole() == Role.OWNER || user.getRole() == mappedRole) {
            return;
        }
        log.info(
                "Syncing role of '{}' from {} to {}",
                user.getUsername(),
                user.getRole(),
                mappedRole);
        user.setRole(mappedRole);
    }

    private String uniqueUsername(OAuthUserInfo userInfo) {
        String base = sanitize(userInfo.username());
        if (base.isEmpty()) {
            base = sanitize(localPart(userInfo.email()));
        }
        if (base.isEmpty()) {
            base = FALLBACK_USERNAME;
        }
        String candidate = base;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "-" + randomSuffix();
        }
        return candidate;
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String localPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(USERNAME_SUFFIX_LENGTH);
        for (int i = 0; i < USERNAME_SUFFIX_LENGTH; i++) {
            suffix.append(
                    USERNAME_SUFFIX_ALPHABET.charAt(
                            random.nextInt(USERNAME_SUFFIX_ALPHABET.length())));
        }
        return suffix.toString();
    }
}
