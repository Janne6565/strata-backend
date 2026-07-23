package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.configuration.web.FrontendProperties;
import com.janne6565.stratabackend.controller.v1.schema.OAuthApi;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.exception.OAuthAccessDeniedException;
import com.janne6565.stratabackend.services.auth.JwtService;
import com.janne6565.stratabackend.services.auth.RefreshCookieFactory;
import com.janne6565.stratabackend.services.auth.oauth.OAuthService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the {@link OAuthApi} contract. On a successful callback it mints Strata's own refresh
 * cookie for the resolved user and redirects home; every failure redirects to the login page with a
 * translated {@code oauthError} code instead of leaking a stack trace.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OAuthController implements OAuthApi {

    private static final String ERROR_NO_ACCESS = "noAccess";
    private static final String ERROR_GENERIC = "true";

    private final OAuthService oAuthService;
    private final JwtService jwtService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final FrontendProperties frontendProperties;

    @Override
    public ResponseEntity<Void> authorize(String provider) {
        try {
            String url = oAuthService.buildAuthorizationUrl(provider);
            return redirect(url);
        } catch (RuntimeException ex) {
            log.warn("Failed to start OAuth authorize for provider {}", provider, ex);
            return redirect(loginError(ERROR_GENERIC));
        }
    }

    @Override
    public ResponseEntity<Void> callback(String provider, String code, String state) {
        UserEntity user;
        try {
            user = oAuthService.handleLoginCallback(provider, code, state);
        } catch (OAuthAccessDeniedException ex) {
            log.warn("OAuth login denied for provider {}: {}", provider, ex.getMessage());
            return redirect(loginError(ERROR_NO_ACCESS));
        } catch (RuntimeException ex) {
            log.error("OAuth callback failed for provider {}", provider, ex);
            return redirect(loginError(ERROR_GENERIC));
        }

        String refreshToken = jwtService.issueRefreshToken(user).token();
        ResponseCookie cookie = refreshCookieFactory.create(refreshToken);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(frontendProperties.url() + "/"))
                .build();
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    private String loginError(String code) {
        return frontendProperties.url() + "/login?oauthError=" + code;
    }
}
