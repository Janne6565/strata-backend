package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies fail-fast wiring and the policy decisions, without a Spring context or Docker. */
class ValidatorRegistryTest {

    private final ResourceResolver resolver = new ResourceResolver(null, null, null);

    private ValidatorRegistry registryWith(Object... beans) {
        ApplicationContext context = mock(ApplicationContext.class);
        Map<String, Object> beanMap =
                java.util.stream.IntStream.range(0, beans.length)
                        .boxed()
                        .collect(java.util.stream.Collectors.toMap(i -> "bean" + i, i -> beans[i]));
        when(context.getBeansOfType(Object.class)).thenReturn(beanMap);
        return new ValidatorRegistry(context);
    }

    @Test
    void wiresEveryOperationWhenAllPoliciesPresent() {
        ValidatorRegistry registry = registryWith(
                        new UserPolicies(),
                        new GrantPolicies(),
                        new GroupPolicies(),
                        new DiscoveryPolicies(),
                        new DatabaseAccessPolicies(null));

        registry.afterSingletonsInstantiated(); // must not throw — all operations covered
    }

    @Test
    void failsFastWhenAnOperationHasNoPolicy() {
        ValidatorRegistry registry = registryWith(); // no policies at all

        assertThatThrownBy(registry::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No @Validates policy");
    }

    @Test
    void adminPassesAndPlainUserFailsUserListPolicy() {
        ValidatorRegistry registry = registryWith(
                        new UserPolicies(),
                        new GrantPolicies(),
                        new GroupPolicies(),
                        new DiscoveryPolicies(),
                        new DatabaseAccessPolicies(null));
        registry.afterSingletonsInstantiated();

        UserEntity admin = new UserEntity("admin", "hash", Role.ADMIN);
        UserEntity plain = new UserEntity("user", "hash", Role.USER);

        assertThat(registry.validate(Operation.USER_LIST, resolver, null, admin)).isTrue();
        assertThat(registry.validate(Operation.USER_LIST, resolver, null, plain)).isFalse();
    }
}
