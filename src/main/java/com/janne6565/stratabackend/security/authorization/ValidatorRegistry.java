package com.janne6565.stratabackend.security.authorization;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import com.janne6565.stratabackend.entity.UserEntity;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Discovers every {@code @Validates} policy method at startup and indexes it by {@link Operation}.
 * Fails fast (the application will not start) if any operation has no validator or more than one —
 * wiring mistakes surface at boot, never at request time (AUTH.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidatorRegistry implements SmartInitializingSingleton {


    private final ApplicationContext applicationContext;
    private final Map<Operation, Validator> validators = new EnumMap<>(Operation.class);


    @Override
    public void afterSingletonsInstantiated() {
        for (Object bean : applicationContext.getBeansOfType(Object.class).values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(
                    targetClass,
                    method -> register(bean, method),
                    method -> AnnotationUtils.findAnnotation(method, Validates.class) != null);
        }
        for (Operation operation : Operation.values()) {
            if (!validators.containsKey(operation)) {
                throw new IllegalStateException(
                        "No @Validates policy registered for operation " + operation);
            }
        }
        log.info("Authorization registry: {} operations wired", validators.size());
    }

    private void register(Object bean, Method method) {
        Operation operation = AnnotationUtils.findAnnotation(method, Validates.class).value();
        if (!isValidSignature(method)) {
            throw new IllegalStateException(
                    "@Validates method "
                            + method
                            + " must be (ResourceResolver, Object, UserEntity) -> boolean");
        }
        Validator existing = validators.get(operation);
        if (existing != null) {
            throw new IllegalStateException(
                    "Duplicate @Validates policy for operation "
                            + operation
                            + ": "
                            + existing.method()
                            + " and "
                            + method);
        }
        ReflectionUtils.makeAccessible(method);
        validators.put(operation, new Validator(bean, method));
    }

    private boolean isValidSignature(Method method) {
        Class<?>[] params = method.getParameterTypes();
        return (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)
                && params.length == 3
                && params[0] == ResourceResolver.class
                && params[1] == Object.class
                && params[2] == UserEntity.class;
    }

    /** Runs the policy for an operation. Returns the policy's boolean decision. */
    public boolean validate(
            Operation operation, ResourceResolver resolver, Object referenceId, UserEntity user) {
        Validator validator = validators.get(operation);
        if (validator == null) {
            // Unreachable after fail-fast startup, but defend against misuse.
            throw new IllegalStateException("No validator for operation " + operation);
        }
        try {
            return (boolean) validator.method().invoke(validator.bean(), resolver, referenceId, user);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Policy invocation failed for " + operation, ex.getCause());
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Policy not accessible for " + operation, ex);
        }
    }

    private record Validator(Object bean, Method method) {}
}
