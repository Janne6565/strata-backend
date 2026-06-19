package com.janne6565.stratabackend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.common.UnauthorizedException;
import com.janne6565.stratabackend.config.properties.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Pure-JVM tests for token issuing/verification (no Spring context, no Docker). */
class JwtServiceTest {

    private static final String SECRET = "0123456789-this-secret-is-long-enough-for-hs256";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, Duration.ofHours(1), "strata"));

    @Test
    void issuesTokenThatParsesBackToTheSameUserId() {
        User user = new User("alice", "hash", Role.ADMIN);

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
        String foreignToken = other.issue(new User("mallory", "hash", Role.USER)).token();

        assertThatThrownBy(() -> jwtService.parseUserId(foreignToken))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService shortLived =
                new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1), "strata"));
        String expired = shortLived.issue(new User("bob", "hash", Role.USER)).token();

        assertThatThrownBy(() -> jwtService.parseUserId(expired))
                .isInstanceOf(UnauthorizedException.class);
    }
}
