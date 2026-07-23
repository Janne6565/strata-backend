package com.janne6565.stratabackend.services.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.configuration.security.JwtProperties;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.exception.UnauthorizedException;
import com.janne6565.stratabackend.services.auth.JwtService.TokenType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Pure-JVM tests for token issuing/verification (no Spring context, no Docker). */
class JwtServiceTest {

    private static final String SECRET = "0123456789-this-secret-is-long-enough-for-hs256";

    private final JwtService jwtService =
            new JwtService(
                    new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(30), "strata"));

    @Test
    void issuesAccessTokenThatParsesBackToTheSameUserId() {
        UserEntity user = new UserEntity("alice", "hash", Role.ADMIN);

        JwtService.IssuedToken issued = jwtService.issueAccessToken(user);

        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(jwtService.parseUserId(issued.token(), TokenType.ACCESS))
                .isEqualTo(user.getId());
    }

    @Test
    void issuesRefreshTokenThatParsesBackToTheSameUserId() {
        UserEntity user = new UserEntity("alice", "hash", Role.ADMIN);

        JwtService.IssuedToken issued = jwtService.issueRefreshToken(user);

        assertThat(jwtService.parseUserId(issued.token(), TokenType.REFRESH))
                .isEqualTo(user.getId());
    }

    @Test
    void rejectsRefreshTokenWhenAccessExpected() {
        UserEntity user = new UserEntity("alice", "hash", Role.ADMIN);
        String refresh = jwtService.issueRefreshToken(user).token();

        assertThatThrownBy(() -> jwtService.parseUserId(refresh, TokenType.ACCESS))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAccessTokenWhenRefreshExpected() {
        UserEntity user = new UserEntity("alice", "hash", Role.ADMIN);
        String access = jwtService.issueAccessToken(user).token();

        assertThatThrownBy(() -> jwtService.parseUserId(access, TokenType.REFRESH))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> jwtService.parseUserId("not-a-jwt", TokenType.ACCESS))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService other =
                new JwtService(
                        new JwtProperties(
                                "a-totally-different-secret-key-also-long-enough!",
                                Duration.ofHours(1),
                                Duration.ofDays(30),
                                "strata"));
        String foreignToken =
                other.issueAccessToken(new UserEntity("mallory", "hash", Role.USER)).token();

        assertThatThrownBy(() -> jwtService.parseUserId(foreignToken, TokenType.ACCESS))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService shortLived =
                new JwtService(
                        new JwtProperties(
                                SECRET, Duration.ofSeconds(-1), Duration.ofDays(30), "strata"));
        String expired =
                shortLived.issueAccessToken(new UserEntity("bob", "hash", Role.USER)).token();

        assertThatThrownBy(() -> jwtService.parseUserId(expired, TokenType.ACCESS))
                .isInstanceOf(UnauthorizedException.class);
    }
}
