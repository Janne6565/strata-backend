package com.janne6565.stratabackend.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.janne6565.stratabackend.common.BadRequestException;
import com.janne6565.stratabackend.engine.jdbc.JdbcConnectionPool;
import com.janne6565.stratabackend.engine.postgres.PostgresEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

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
