package com.janne6565.stratabackend.services.auth;

import com.janne6565.stratabackend.configuration.security.AuthProperties;
import com.janne6565.stratabackend.configuration.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code strata_refresh} httpOnly refresh cookie. {@code SameSite=Lax} is required so
 * the cookie survives the top-level redirect back from the OAuth provider; the path is scoped to
 * {@code /api/v1/auth} so it is only sent to the token/logout endpoints, and {@code Secure} follows
 * {@code strata.auth.cookie-secure} (true in every real deployment).
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "strata_refresh";

    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE_LAX = "Lax";

    private final AuthProperties authProperties;
    private final JwtProperties jwtProperties;

    /** A cookie carrying the refresh token, living as long as the refresh token itself. */
    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(jwtProperties.refreshTokenTtl()).build();
    }

    /** An immediately-expiring cookie that clears any existing refresh cookie (logout). */
    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .path(COOKIE_PATH)
                .sameSite(SAME_SITE_LAX);
    }
}
