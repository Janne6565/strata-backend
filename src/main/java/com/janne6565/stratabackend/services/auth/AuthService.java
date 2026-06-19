package com.janne6565.stratabackend.services.auth;
import lombok.RequiredArgsConstructor;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.LoginRequest;
import com.janne6565.stratabackend.model.core.LoginResponse;
import com.janne6565.stratabackend.model.core.UserResponse;
import com.janne6565.stratabackend.model.exception.UnauthorizedException;
import com.janne6565.stratabackend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies credentials and issues access tokens. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserEntity user =
                userRepository
                        .findByUsername(request.username())
                        .filter(UserEntity::isEnabled)
                        .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                        // Same message whether the user is unknown, disabled, or the password is
                        // wrong — never reveal which (AUTH.md).
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), issued.expiresAt(), UserResponse.from(user));
    }
}
