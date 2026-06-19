package com.janne6565.stratabackend.kubernetes;

import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.common.BadRequestException;
import com.janne6565.stratabackend.engine.ConnectionDetails;
import com.janne6565.stratabackend.engine.ConnectionProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Lazily creates and caches a connection pool per datasource (ARCHITECTURE.md §9): pools are built
 * on first use, keyed by datasource id, idle-evicted, and hold the resolved credentials in memory
 * only. Connections reach target databases over cluster DNS.
 */
@Component
public class ConnectionManager implements ConnectionProvider {

    private final CredentialReader credentialReader;
    private final ConcurrentHashMap<UUID, HikariDataSource> pools = new ConcurrentHashMap<>();

    public ConnectionManager(CredentialReader credentialReader) {
        this.credentialReader = credentialReader;
    }

    @Override
    public Connection getConnection(Datasource datasource) throws SQLException {
        return pools.computeIfAbsent(datasource.getId(), id -> createPool(datasource)).getConnection();
    }

    /** Drops the pool for a datasource (e.g. on unregister or credential rotation). */
    public void evict(UUID datasourceId) {
        HikariDataSource pool = pools.remove(datasourceId);
        if (pool != null) {
            pool.close();
        }
    }

    private HikariDataSource createPool(Datasource datasource) {
        ConnectionDetails details = credentialReader.read(datasource);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl(details));
        config.setUsername(details.username());
        config.setPassword(details.password());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setPoolName("strata-" + datasource.getId());
        return new HikariDataSource(config);
    }

    private String jdbcUrl(ConnectionDetails details) {
        String database = details.database() == null ? "" : details.database();
        return switch (details.driver()) {
            case "postgresql" ->
                    "jdbc:postgresql://" + details.host() + ":" + details.port() + "/" + database;
            case "mysql" ->
                    "jdbc:mysql://" + details.host() + ":" + details.port() + "/" + database;
            default ->
                    throw new BadRequestException(
                            "No JDBC URL builder for driver: " + details.driver());
        };
    }

    @PreDestroy
    public void closeAll() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
