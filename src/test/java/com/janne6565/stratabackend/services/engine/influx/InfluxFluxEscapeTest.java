package com.janne6565.stratabackend.services.engine.influx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for Flux string-literal escaping (no live InfluxDB required). */
class InfluxFluxEscapeTest {

    @Test
    void leavesOrdinaryNamesUntouched() {
        assertThat(InfluxEngine.flux("weather")).isEqualTo("weather");
        assertThat(InfluxEngine.flux("cpu_usage.percent")).isEqualTo("cpu_usage.percent");
    }

    @Test
    void escapesDoubleQuotesSoNamesCannotBreakOutOfTheString() {
        // A measurement named weather" must not close the Flux string literal.
        assertThat(InfluxEngine.flux("weather\"")).isEqualTo("weather\\\"");
        assertThat(InfluxEngine.flux("a\" or true or \"")).isEqualTo("a\\\" or true or \\\"");
    }

    @Test
    void escapesBackslashesBeforeQuotes() {
        // Backslash is escaped first so it can't form an unintended escape with a later quote.
        assertThat(InfluxEngine.flux("a\\")).isEqualTo("a\\\\");
        assertThat(InfluxEngine.flux("a\\\"")).isEqualTo("a\\\\\\\"");
    }

    @Test
    void neutralisesStringInterpolation() {
        assertThat(InfluxEngine.flux("${r._field}")).isEqualTo("\\${r._field}");
    }

    @Test
    void treatsNullAsEmpty() {
        assertThat(InfluxEngine.flux(null)).isEmpty();
    }
}
