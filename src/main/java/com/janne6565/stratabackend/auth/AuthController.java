package com.janne6565.stratabackend.auth;

import com.janne6565.stratabackend.auth.dto.LoginRequest;
import com.janne6565.stratabackend.auth.dto.LoginResponse;
import com.janne6565.stratabackend.auth.dto.UserResponse;
import org.springframework.web.bind.annotation.RestController;

/** Implements the {@link AuthApi} contract; thin delegation to {@link AuthService}/{@link CurrentUser}. */
@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        return authService.login(request);
    }

    @Override
    public UserResponse me() {
        return UserResponse.from(currentUser.require());
    }
}
