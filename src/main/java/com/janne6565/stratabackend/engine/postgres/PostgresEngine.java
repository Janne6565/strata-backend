package com.janne6565.stratabackend.engine.postgres;

import com.janne6565.stratabackend.engine.ConnectionDetails;
import com.janne6565.stratabackend.engine.jdbc.AbstractJdbcEngine;
import com.janne6565.stratabackend.engine.jdbc.JdbcConnectionPool;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL adapter (ARCHITECTURE.md §9). The database is the JDBC schema; identifiers are
 * double-quoted. Read-only is enforced by the shared read-only-transaction logic, which under
 * pgjdbc's default {@code readOnlyMode=transaction} rejects writes at the server.
 */
@Component
public class PostgresEngine extends AbstractJdbcEngine {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of("pg_catalog", "information_schema");

    public PostgresEngine(JdbcConnectionPool connectionPool) {
        super(connectionPool);
    }

    @Override
    public String driver() {
        return "postgresql";
    }

    @Override
    protected String jdbcUrl(ConnectionDetails details) {
        String database = details.database() == null ? "" : details.database();
        return "jdbc:postgresql://" + details.host() + ":" + details.port() + "/" + database;
    }

    @Override
    protected Set<String> systemSchemas() {
        return SYSTEM_SCHEMAS;
    }

    @Override
    protected String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    protected boolean databaseIsCatalog() {
        return false;
    }
}
