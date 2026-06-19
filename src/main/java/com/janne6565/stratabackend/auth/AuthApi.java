package com.janne6565.stratabackend.auth;

import com.janne6565.stratabackend.auth.dto.LoginRequest;
import com.janne6565.stratabackend.auth.dto.LoginResponse;
import com.janne6565.stratabackend.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Authentication contract. This interface is the API-first source of truth: springdoc renders it
 * into {@code openapi.json}, from which the frontend's Orval client is generated (ARCHITECTURE.md
 * §7). The controller implements it; mappings and docs live here.
 */
@Tag(name = "Authentication")
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public interface AuthApi {

    @Operation(summary = "Authenticate with username/password and receive an access token")
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    LoginResponse login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "Return the currently authenticated user")
    @GetMapping("/me")
    UserResponse me();
}
