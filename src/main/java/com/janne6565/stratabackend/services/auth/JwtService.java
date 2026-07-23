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
 * Issues and verifies the application's self-signed tokens (HMAC-SHA256). Stateless: the subject is
 * the user id; {@code username}/{@code role} are carried as convenience claims, but the {@link
 * JwtAuthenticationFilter} always reloads the user to honour current state.
 *
 * <p>Every token carries a {@code tokenType} claim so the two roles can never be confused: a
 * short-lived {@link TokenType#ACCESS} token authenticates each request (accepted by the filter),
 * while a long-lived {@link TokenType#REFRESH} token (delivered as an httpOnly cookie) only mints
 * fresh access tokens at {@code POST /api/v1/auth/token}.
 */
@Service
public class JwtService {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    /** The two self-issued token roles, distinguished by the {@code tokenType} claim. */
    public enum TokenType {
        ACCESS("access"),
        REFRESH("refresh");

        private final String claimValue;

        TokenType(String claimValue) {
            this.claimValue = claimValue;
        }

        public String claimValue() {
            return claimValue;
        }
    }

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** A claim-rich access token: subject, username, role and {@code tokenType=access}. */
    public IssuedToken issueAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String token =
                baseBuilder(user, now, expiresAt)
                        .claim(CLAIM_ROLE, user.getRole().name())
                        .claim(CLAIM_TOKEN_TYPE, TokenType.ACCESS.claimValue())
                        .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** A minimal, long-lived refresh token: subject, username and {@code tokenType=refresh}. */
    public IssuedToken issueRefreshToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        String token =
                baseBuilder(user, now, expiresAt)
                        .claim(CLAIM_TOKEN_TYPE, TokenType.REFRESH.claimValue())
                        .compact();
        return new IssuedToken(token, expiresAt);
    }

    private io.jsonwebtoken.JwtBuilder baseBuilder(
            UserEntity user, Instant now, Instant expiresAt) {
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key);
    }

    /**
     * Verifies the signature, issuer, expiry and that the token is of the expected {@link
     * TokenType}; returns the subject user id. Rejects any mismatch with {@link
     * UnauthorizedException} so an access endpoint can never be reached with a refresh token (or
     * vice versa).
     */
    public UUID parseUserId(String token, TokenType expectedType) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .requireIssuer(properties.issuer())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            String type = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!expectedType.claimValue().equals(type)) {
                throw new UnauthorizedException("Unexpected token type");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    /** An issued token paired with its absolute expiry. */
    public record IssuedToken(String token, Instant expiresAt) {}
}
