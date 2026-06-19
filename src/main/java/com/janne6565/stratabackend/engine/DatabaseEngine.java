package com.janne6565.stratabackend.engine;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Per-engine adapter SPI (ARCHITECTURE.md §9). Each implementation knows how to introspect, browse
 * and query one database engine over a live {@link Connection} supplied by the connection manager.
 * Detection is handled separately by the config-driven {@code DetectorMatcher} (M2), so it is not
 * part of this SPI. Implementations must be stateless and thread-safe.
 */
public interface DatabaseEngine {

    /** The driver id this engine handles (matches {@code datasource.driver}), e.g. {@code postgresql}. */
    String driver();

    /** Lists the user-visible tables/views and their columns. */
    SchemaInfo introspect(Connection connection) throws SQLException;

    /** Returns a page of rows from a table/view. */
    RowPage browse(Connection connection, ObjectRef ref, int offset, int limit) throws SQLException;

    /** Runs an arbitrary query. In {@link QueryMode#READ} the connection is held read-only. */
    QueryResult runQuery(Connection connection, String sql, QueryMode mode) throws SQLException;

    /** Whether this engine can technically enforce read-only at the connection/driver level. */
    boolean canEnforceReadOnly();
}
