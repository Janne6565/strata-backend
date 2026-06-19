package com.janne6565.stratabackend.engine.mysql;

import com.janne6565.stratabackend.engine.ConnectionDetails;
import com.janne6565.stratabackend.engine.jdbc.AbstractJdbcEngine;
import com.janne6565.stratabackend.engine.jdbc.JdbcConnectionPool;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * MySQL adapter (ARCHITECTURE.md §9). The database is the JDBC catalog; identifiers are
 * backtick-quoted. Read-only is enforced by the shared read-only-transaction logic
 * ({@code SET TRANSACTION READ ONLY}).
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
}
