package com.janne6565.stratabackend.configuration.security;

import com.janne6565.stratabackend.security.jwtfilter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Single source of HTTP security rules (AUTH.md). Stateless JWT API: no sessions, CSRF disabled
 * (the documented exception), the {@link JwtAuthenticationFilter} runs ahead of the username/
 * password filter, and unauthenticated access to protected routes returns a 401 ProblemDetail.
 */
@Configuration
public class SecurityConfig {

    /**
     * Public endpoints: login, the OpenAPI/Swagger surface used to generate the frontend client,
     * and the actuator health probes (k8s liveness/readiness, scraped unauthenticated). Other
     * actuator endpoints (metrics, etc.) stay behind authentication via the catch-all rule below.
     */
    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/login",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/actuator/health/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_PATHS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
