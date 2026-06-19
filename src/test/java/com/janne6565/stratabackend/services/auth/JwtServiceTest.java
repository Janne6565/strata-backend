package com.janne6565.stratabackend.services.auth;

import com.janne6565.stratabackend.configuration.security.JwtProperties;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.exception.UnauthorizedException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure-JVM tests for token issuing/verification (no Spring context, no Docker). */
class JwtServiceTest {

    private static final String SECRET = "0123456789-this-secret-is-long-enough-for-hs256";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, Duration.ofHours(1), "strata"));

    @Test
    void issuesTokenThatParsesBackToTheSameUserId() {
        UserEntity user = new UserEntity("alice", "hash", Role.ADMIN);

        JwtService.IssuedToken issued = jwtService.issue(user);

        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(jwtService.parseUserId(issued.token())).isEqualTo(user.getId());
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> jwtService.parseUserId("not-a-jwt"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService other =
                new JwtService(
                        new JwtProperties(
                                "a-totally-different-secret-key-also-long-enough!",
                                Duration.ofHours(1),
                                "strata"));
        String foreignToken = other.issue(new UserEntity("mallory", "hash", Role.USER)).token();

        assertThatThrownBy(() -> jwtService.parseUserId(foreignToken))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService shortLived =
                new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1), "strata"));
        String expired = shortLived.issue(new UserEntity("bob", "hash", Role.USER)).token();

        assertThatThrownBy(() -> jwtService.parseUserId(expired))
                .isInstanceOf(UnauthorizedException.class);
    }
}
