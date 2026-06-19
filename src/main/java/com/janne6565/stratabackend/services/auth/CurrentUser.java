package com.janne6565.stratabackend.services.auth;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.exception.UnauthorizedException;
import com.janne6565.stratabackend.security.jwtfilter.JwtAuthenticationFilter;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated {@link UserEntity} placed in the security context by {@link
 * JwtAuthenticationFilter}.
 */
@Component
public class CurrentUser {

    public Optional<UserEntity> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserEntity user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public UserEntity require() {
        return get().orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }
}
