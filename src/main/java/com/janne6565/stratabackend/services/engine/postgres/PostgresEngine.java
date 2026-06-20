package com.janne6565.stratabackend.services.engine.postgres;

import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.services.engine.jdbc.AbstractJdbcEngine;
import com.janne6565.stratabackend.services.engine.jdbc.JdbcConnectionPool;
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

    @Override
    protected String connectionsSql() {
        return "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()";
    }

    @Override
    protected String dataSizeSql() {
        return "SELECT pg_database_size(current_database())";
    }

    @Override
    protected String objectCountSql() {
        return "SELECT count(*) FROM information_schema.tables"
                + " WHERE table_schema NOT IN ('pg_catalog', 'information_schema')";
    }

    @Override
    protected String rowCountEstimateSql() {
        // reltuples is the planner's estimate from the last ANALYZE/VACUUM; -1 means never
        // analysed, which the caller drops. relkind r/p = ordinary/partitioned tables.
        return "SELECT n.nspname, c.relname, c.reltuples::bigint"
                + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                + " WHERE c.relkind IN ('r', 'p')"
                + " AND n.nspname NOT IN ('pg_catalog', 'information_schema')";
    }
}
