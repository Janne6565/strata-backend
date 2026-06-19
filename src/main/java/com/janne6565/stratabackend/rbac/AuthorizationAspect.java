package com.janne6565.stratabackend.rbac;

import com.janne6565.stratabackend.auth.CurrentUser;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.common.ForbiddenException;
import java.lang.annotation.Annotation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link NeedsValidation} on controller methods (ARCHITECTURE.md §6): loads the current
 * user (401 if none), extracts the {@link ResourceId} argument, runs the operation's policy via
 * {@link ValidatorRegistry}, and throws 403 if the policy denies. This is layer 1 of the three
 * enforcement layers — single-resource gating.
 */
@Aspect
@Component
public class AuthorizationAspect {

    private final CurrentUser currentUser;
    private final ValidatorRegistry validatorRegistry;
    private final ResourceResolver resourceResolver;

    public AuthorizationAspect(
            CurrentUser currentUser,
            ValidatorRegistry validatorRegistry,
            ResourceResolver resourceResolver) {
        this.currentUser = currentUser;
        this.validatorRegistry = validatorRegistry;
        this.resourceResolver = resourceResolver;
    }

    @Before("@annotation(needsValidation)")
    public void authorize(JoinPoint joinPoint, NeedsValidation needsValidation) {
        User user = currentUser.require();
        Object referenceId = extractResourceId(joinPoint);
        boolean allowed =
                validatorRegistry.validate(
                        needsValidation.value(), resourceResolver, referenceId, user);
        if (!allowed) {
            throw new ForbiddenException(
                    "Not permitted to perform " + needsValidation.value());
        }
    }

    /** Returns the value of the parameter annotated {@link ResourceId}, or null if there is none. */
    private Object extractResourceId(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations =
                signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof ResourceId) {
                    return args[i];
                }
            }
        }
        return null;
    }
}
