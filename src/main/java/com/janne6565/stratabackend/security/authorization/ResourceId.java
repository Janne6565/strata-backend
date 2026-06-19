package com.janne6565.stratabackend.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the method parameter that identifies the target resource of a {@link NeedsValidation}
 * operation (e.g. the target user's id). Passed to the policy as its {@code referenceId}; absent
 * for collection/create operations, in which case the policy receives {@code null}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceId {}
