package com.janne6565.stratabackend.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as requiring authorization for a given {@link Operation}. The {@link
 * AuthorizationAspect} intercepts the call, resolves the current user and the parameter annotated
 * {@link ResourceId}, and runs the registered policy before the method executes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NeedsValidation {
    Operation value();
}
