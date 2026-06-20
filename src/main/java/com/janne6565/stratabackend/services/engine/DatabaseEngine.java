package com.janne6565.stratabackend.services.engine;

import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import java.util.Optional;

/**
 * Per-engine adapter SPI (ARCHITECTURE.md §9). Each implementation knows how to introspect, browse
 * and query one database engine, given the resolved {@link ConnectionDetails}. The adapter owns its
 * own connection/client lifecycle (pooling, sessions) — JDBC engines share a pool via {@code
 * AbstractJdbcEngine}, NoSQL/time-series engines manage their own clients. Detection is handled
 * separately by the config-driven {@code DetectorMatcher} (M2), so it is not part of this SPI.
 * Implementations must be stateless across calls and thread-safe; failures surface as {@link
 * EngineException}.
 */
public interface DatabaseEngine {

    /**
     * The driver id this engine handles (matches {@code datasource.driver}), e.g. {@code
     * postgresql}.
     */
    String driver();

    /** Lists the user-visible tables/views/collections and their columns/fields. */
    SchemaInfo introspect(ConnectionDetails details);

    /** Returns a page of rows/documents from a table/view/collection. */
    RowPage browse(ConnectionDetails details, ObjectRef ref, int offset, int limit);

    /**
     * Runs an arbitrary query/command. In {@link QueryMode#READ} writes are rejected at the engine.
     */
    QueryResult runQuery(ConnectionDetails details, String query, QueryMode mode);

    /** Whether this engine can technically enforce read-only at the connection/driver level. */
    boolean canEnforceReadOnly();

    /**
     * Best-effort live resource telemetry for the database (active connections, on-disk size,
     * object count). Opt-in per adapter — the default returns {@link Optional#empty()} for engines
     * that don't expose it. Implementations must not throw for "unsupported"; reserve {@link
     * EngineException} for an actual sampling failure (the caller treats it as "unavailable").
     */
    default Optional<EngineMetrics> sampleMetrics(ConnectionDetails details) {
        return Optional.empty();
    }
}
