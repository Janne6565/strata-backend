package com.janne6565.stratabackend.engine.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Shared HikariCP pool cache for the JDBC engines (ARCHITECTURE.md §9). Pools are built lazily on
 * first use and keyed by JDBC URL + username, so every datasource backed by the same endpoint and
 * principal shares one bounded pool. Resolved credentials live only inside the pool config, in
 * memory (AUTH.md). Connections reach target databases over cluster DNS.
 */
@Component
public class JdbcConnectionPool implements AutoCloseable {

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    /** Borrows a pooled connection for the given endpoint; the pool is created on first use. */
    public Connection connection(String jdbcUrl, String username, String password)
            throws SQLException {
        return pools.computeIfAbsent(key(jdbcUrl, username), k -> build(jdbcUrl, username, password))
                .getConnection();
    }

    private String key(String jdbcUrl, String username) {
        return jdbcUrl + "|" + username;
    }

    private HikariDataSource build(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        return new HikariDataSource(config);
    }

    @PreDestroy
    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
