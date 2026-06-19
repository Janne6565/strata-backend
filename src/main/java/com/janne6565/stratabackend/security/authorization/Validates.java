package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.UserEntity;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a policy method implementing the authorization decision for an {@link Operation}. The
 * method must be {@code public boolean (ResourceResolver, Object referenceId, UserEntity)} and live in a
 * Spring bean; {@link ValidatorRegistry} discovers it at startup. Keep policies pure and
 * side-effect free so they can be unit-tested directly (AUTH.md).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Validates {
    Operation value();
}
