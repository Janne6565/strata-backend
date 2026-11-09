package com.janne6565.stratabackend.services.backup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgDumpRunnerTest {

    @Test
    void stripsTheJdbcPrefix() {
        assertThat(PgDumpRunner.toLibpqUri("jdbc:postgresql://db:5432/strata"))
                .isEqualTo("postgresql://db:5432/strata");
    }

    @Test
    void dropsPgjdbcOnlyParametersButKeepsLibpqOnes() {
        assertThat(
                        PgDumpRunner.toLibpqUri(
                                "jdbc:postgresql://localhost:32780/test?loggerLevel=OFF"))
                .isEqualTo("postgresql://localhost:32780/test");
        assertThat(
                        PgDumpRunner.toLibpqUri(
                                "jdbc:postgresql://db/strata?loggerLevel=OFF&sslmode=require&prepareThreshold=0"))
                .isEqualTo("postgresql://db/strata?sslmode=require");
    }
}
