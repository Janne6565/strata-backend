package com.janne6565.stratabackend.services.auth;

import com.janne6565.stratabackend.configuration.security.JwtProperties;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.exception.UnauthorizedException;
import com.janne6565.stratabackend.security.jwtfilter.JwtAuthenticationFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the application's self-signed access tokens (HMAC-SHA256). Stateless: the
 * subject is the user id; {@code username}/{@code role} are carried as convenience claims, but
 * the {@link JwtAuthenticationFilter} always reloads the user to honour current state.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String token =
                Jwts.builder()
                        .issuer(properties.issuer())
                        .subject(user.getId().toString())
                        .claim("username", user.getUsername())
                        .claim("role", user.getRole().name())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiresAt))
                        .signWith(key)
                        .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** Verifies the signature, issuer and expiry; returns the subject user id. */
    public UUID parseUserId(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .requireIssuer(properties.issuer())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    /** An issued access token paired with its absolute expiry. */
    public record IssuedToken(String token, Instant expiresAt) {}
}
