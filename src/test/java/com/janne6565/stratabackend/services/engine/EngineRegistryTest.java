package com.janne6565.stratabackend.services.engine;

import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.services.engine.jdbc.JdbcConnectionPool;
import com.janne6565.stratabackend.services.engine.postgres.PostgresEngine;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineRegistryTest {

    private final EngineRegistry registry =
            new EngineRegistry(List.of(new PostgresEngine(new JdbcConnectionPool())));

    @Test
    void resolvesEngineByDriver() {
        assertThat(registry.forDriver("postgresql")).isInstanceOf(PostgresEngine.class);
        assertThat(registry.supports("postgresql")).isTrue();
    }

    @Test
    void rejectsUnknownDriver() {
        assertThat(registry.supports("mongodb")).isFalse();
        assertThatThrownBy(() -> registry.forDriver("mongodb"))
                .isInstanceOf(BadRequestException.class);
    }
}
