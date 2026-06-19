package com.janne6565.stratabackend.engine;

import com.janne6565.stratabackend.catalog.Datasource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Supplies live JDBC connections to a target datasource. The seam between the browse/query services
 * and the pooled, credential-resolving {@code ConnectionManager} — letting the engine path be
 * tested against a real database without the Kubernetes credential machinery.
 */
public interface ConnectionProvider {

    Connection getConnection(Datasource datasource) throws SQLException;
}
