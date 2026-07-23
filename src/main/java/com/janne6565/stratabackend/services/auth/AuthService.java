package com.janne6565.stratabackend.services.auth;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.LoginRequest;
import com.janne6565.stratabackend.model.core.LoginResponse;
import com.janne6565.stratabackend.model.core.UserResponse;
import com.janne6565.stratabackend.model.exception.UnauthorizedException;
import com.janne6565.stratabackend.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies credentials and mints the access/refresh token pair. A successful login (password or
 * refresh) yields both the {@link LoginResponse} body (access token in {@code Authorization}) and
 * the rotated {@code strata_refresh} cookie the controller sets on the response.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshCookieFactory refreshCookieFactory;

    @Transactional(readOnly = true)
    public AuthenticatedSession login(LoginRequest request) {
        UserEntity user =
                userRepository
                        .findByUsername(request.username())
                        .filter(UserEntity::isEnabled)
                        // OAuth-only users have no password hash — never feed null to the encoder,
                        // and fail with the same generic message as any other bad credential.
                        .filter(u -> u.getPasswordHash() != null)
                        .filter(
                                u ->
                                        passwordEncoder.matches(
                                                request.password(), u.getPasswordHash()))
                        // Same message whether the user is unknown, disabled, password-less, or the
                        // password is wrong — never reveal which (AUTH.md).
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        return buildSession(user);
    }

    /**
     * Exchanges a refresh token (from the {@code strata_refresh} cookie) for a fresh access token
     * and a rotated refresh cookie. Rejects a missing, malformed, expired, non-refresh, or
     * disabled-user token with {@link UnauthorizedException}.
     */
    @Transactional(readOnly = true)
    public AuthenticatedSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }
        UUID userId = jwtService.parseUserId(refreshToken, JwtService.TokenType.REFRESH);
        UserEntity user =
                userRepository
                        .findById(userId)
                        .filter(UserEntity::isEnabled)
                        .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        return buildSession(user);
    }

    /** The clearing cookie for logout — the controller returns it with a 204. */
    public ResponseCookie logoutCookie() {
        return refreshCookieFactory.expire();
    }

    /** Mints the access token + refresh cookie for an already-authenticated user. */
    public AuthenticatedSession buildSession(UserEntity user) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        JwtService.IssuedToken refresh = jwtService.issueRefreshToken(user);
        ResponseCookie cookie = refreshCookieFactory.create(refresh.token());
        LoginResponse body =
                new LoginResponse(access.token(), access.expiresAt(), UserResponse.from(user));
        return new AuthenticatedSession(body, cookie);
    }

    /** The login response body plus the refresh cookie to set on the HTTP response. */
    public record AuthenticatedSession(LoginResponse body, ResponseCookie refreshCookie) {}
}
