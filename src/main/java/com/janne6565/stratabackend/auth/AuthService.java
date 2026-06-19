package com.janne6565.stratabackend.auth;

import com.janne6565.stratabackend.auth.dto.LoginRequest;
import com.janne6565.stratabackend.auth.dto.LoginResponse;
import com.janne6565.stratabackend.auth.dto.UserResponse;
import com.janne6565.stratabackend.common.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies credentials and issues access tokens. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByUsername(request.username())
                        .filter(User::isEnabled)
                        .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                        // Same message whether the user is unknown, disabled, or the password is
                        // wrong — never reveal which (AUTH.md).
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), issued.expiresAt(), UserResponse.from(user));
    }
}
