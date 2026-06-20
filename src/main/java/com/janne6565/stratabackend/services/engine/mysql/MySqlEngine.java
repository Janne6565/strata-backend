package com.janne6565.stratabackend.services.engine.mysql;

import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.services.engine.jdbc.AbstractJdbcEngine;
import com.janne6565.stratabackend.services.engine.jdbc.JdbcConnectionPool;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * MySQL adapter (ARCHITECTURE.md §9). The database is the JDBC catalog; identifiers are
 * backtick-quoted. Read-only is enforced by the shared read-only-transaction logic ({@code SET
 * TRANSACTION READ ONLY}).
 */
@Component
public class MySqlEngine extends AbstractJdbcEngine {

    private static final Set<String> SYSTEM_SCHEMAS =
            Set.of("information_schema", "mysql", "performance_schema", "sys");

    public MySqlEngine(JdbcConnectionPool connectionPool) {
        super(connectionPool);
    }

    @Override
    public String driver() {
        return "mysql";
    }

    @Override
    protected String jdbcUrl(ConnectionDetails details) {
        String database = details.database() == null ? "" : details.database();
        return "jdbc:mysql://"
                + details.host()
                + ":"
                + details.port()
                + "/"
                + database
                + "?useSSL=false&allowPublicKeyRetrieval=true";
    }

    @Override
    protected Set<String> systemSchemas() {
        return SYSTEM_SCHEMAS;
    }

    @Override
    protected String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    protected boolean databaseIsCatalog() {
        return true;
    }

    @Override
    protected String connectionsSql() {
        return "SELECT count(*) FROM information_schema.processlist WHERE db = DATABASE()";
    }

    @Override
    protected String dataSizeSql() {
        return "SELECT COALESCE(SUM(data_length + index_length), 0)"
                + " FROM information_schema.tables WHERE table_schema = DATABASE()";
    }

    @Override
    protected String objectCountSql() {
        return "SELECT count(*) FROM information_schema.tables WHERE table_schema = DATABASE()";
    }

    @Override
    protected String rowCountEstimateSql() {
        // table_rows is an estimate for InnoDB (exact for MyISAM); good enough for a row badge.
        return "SELECT table_schema, table_name, table_rows"
                + " FROM information_schema.tables WHERE table_schema = DATABASE()";
    }
}
