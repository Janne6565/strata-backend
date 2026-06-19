package com.janne6565.stratabackend.security.jwtfilter;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.repository.UserRepository;
import com.janne6565.stratabackend.services.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header, verifies it, loads the (enabled) user and
 * populates the {@link SecurityContextHolder}. All authentication lives here — never in services or
 * controllers (AUTH.md). A missing or invalid token leaves the context anonymous; the security
 * chain then rejects protected endpoints via the entry point.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        UUID userId;
        try {
            userId = jwtService.parseUserId(token);
        } catch (RuntimeException ex) {
            // Invalid/expired token — stay anonymous; protected routes 401 via the entry point.
            return;
        }
        userRepository
                .findById(userId)
                .filter(UserEntity::isEnabled)
                .ifPresent(
                        user -> {
                            var authority =
                                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
                            var authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            user, null, List.of(authority));
                            authentication.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
