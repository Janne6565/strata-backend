package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.AuthApi;
import com.janne6565.stratabackend.model.action.LoginRequest;
import com.janne6565.stratabackend.model.core.LoginResponse;
import com.janne6565.stratabackend.model.core.UserResponse;
import com.janne6565.stratabackend.services.auth.AuthService;
import com.janne6565.stratabackend.services.auth.CurrentUser;
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
