package com.janne6565.stratabackend.services.auth;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import com.janne6565.stratabackend.configuration.security.AuthProperties;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Bootstraps the first {@code OWNER} on startup so a fresh deployment is reachable. Runs only when
 * no owner exists; idempotent and a no-op once the system is seeded. Never logs the password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerSeeder implements ApplicationRunner {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.OWNER) > 0) {
            return;
        }
        AuthProperties.Bootstrap bootstrap = authProperties.bootstrap();
        if (bootstrap == null
                || !StringUtils.hasText(bootstrap.username())
                || !StringUtils.hasText(bootstrap.password())) {
            log.warn(
                    "No OWNER exists and strata.auth.bootstrap is not configured — "
                            + "set strata.auth.bootstrap.username/password to seed the first owner.");
            return;
        }
        if (userRepository.existsByUsername(bootstrap.username())) {
            log.warn(
                    "Cannot seed bootstrap owner: username '{}' already exists", bootstrap.username());
            return;
        }
        UserEntity owner =
                new UserEntity(
                        bootstrap.username(),
                        passwordEncoder.encode(bootstrap.password()),
                        Role.OWNER);
        userRepository.save(owner);
        log.info("Seeded bootstrap OWNER '{}'", owner.getUsername());
    }
}
