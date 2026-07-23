package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.AuthApi;
import com.janne6565.stratabackend.model.action.LoginRequest;
import com.janne6565.stratabackend.model.core.LoginResponse;
import com.janne6565.stratabackend.model.core.UserResponse;
import com.janne6565.stratabackend.services.auth.AuthService;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the {@link AuthApi} contract; thin delegation to {@link AuthService}/{@link
 * CurrentUser}. Login and token refresh return the access token in the body and set the rotated
 * refresh cookie; logout clears it.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        return session(authService.login(request));
    }

    @Override
    public ResponseEntity<LoginResponse> token(String refreshToken) {
        return session(authService.refresh(refreshToken));
    }

    @Override
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authService.logoutCookie().toString())
                .build();
    }

    @Override
    public UserResponse me() {
        return UserResponse.from(currentUser.require());
    }

    private ResponseEntity<LoginResponse> session(AuthService.AuthenticatedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, session.refreshCookie().toString())
                .body(session.body());
    }
}
