package com.janne6565.stratabackend.auth;

import com.janne6565.stratabackend.common.UnauthorizedException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the authenticated {@link User} placed in the security context by {@link JwtAuthenticationFilter}. */
@Component
public class CurrentUser {

    public Optional<User> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public User require() {
        return get().orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }
}
