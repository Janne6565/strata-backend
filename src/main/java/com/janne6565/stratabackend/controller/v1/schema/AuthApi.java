package com.janne6565.stratabackend.controller.v1.schema;

import com.janne6565.stratabackend.model.action.LoginRequest;
import com.janne6565.stratabackend.model.core.LoginResponse;
import com.janne6565.stratabackend.model.core.UserResponse;
import com.janne6565.stratabackend.services.auth.RefreshCookieFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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
@RequestMapping(path = "/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public interface AuthApi {

    @Operation(
            summary =
                    "Authenticate with username/password: returns an access token and sets the"
                            + " refresh cookie")
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request);

    @Operation(
            summary =
                    "Exchange the refresh cookie for a fresh access token and rotate the refresh"
                            + " cookie")
    @PostMapping("/token")
    ResponseEntity<LoginResponse> token(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false)
                    String refreshToken);

    @Operation(summary = "Clear the refresh cookie")
    @PostMapping("/logout")
    ResponseEntity<Void> logout();

    @Operation(summary = "Return the currently authenticated user")
    @GetMapping("/me")
    UserResponse me();
}
