package com.janne6565.stratabackend.services.engine.jdbc;

import com.janne6565.stratabackend.model.core.ColumnInfo;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.TableInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.janne6565.stratabackend.services.engine.DatabaseEngine;
import com.janne6565.stratabackend.services.engine.EngineMetrics;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Shared JDBC implementation of the engine SPI: metadata introspection, paged browse and
 * read-only-transaction query execution. Connections are borrowed from the shared {@link
 * JdbcConnectionPool} for the resolved {@link ConnectionDetails}; {@link SQLException}s are wrapped
 * in {@link EngineException}. Subclasses supply the engine-specific bits — driver id, JDBC URL,
 * identifier quoting, system schemas, and whether the database name lives in the JDBC catalog
 * (MySQL) or schema (PostgreSQL).
 */
public abstract class AbstractJdbcEngine implements DatabaseEngine {

    /** Cap on rows materialised from an ad-hoc query result, to bound memory. */
    protected static final int MAX_QUERY_ROWS = 1000;

    private final JdbcConnectionPool connectionPool;

    protected AbstractJdbcEngine(JdbcConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /** Builds the JDBC URL for this dialect from the resolved connection details. */
    protected abstract String jdbcUrl(ConnectionDetails details);

    /** Schemas/catalogs to hide from introspection. */
    protected abstract Set<String> systemSchemas();

    /** Quotes an identifier for this dialect. */
    protected abstract String quote(String identifier);

    /**
     * True when the database name is the JDBC catalog (MySQL) rather than the schema (PostgreSQL).
     */
    protected abstract boolean databaseIsCatalog();

    @Override
    public boolean canEnforceReadOnly() {
        return true;
    }

    /**
     * SQL returning the active connection count for this database in column 1; {@code null} skips.
     */
    protected String connectionsSql() {
        return null;
    }

    /**
     * SQL returning the on-disk size in bytes for this database in column 1; {@code null} skips.
     */
    protected String dataSizeSql() {
        return null;
    }

    /**
     * SQL returning the user-object (table/view) count for this database in column 1; null skips.
     */
    protected String objectCountSql() {
        return null;
    }

    @Override
    public Optional<EngineMetrics> sampleMetrics(ConnectionDetails details) {
        try (Connection connection = connection(details)) {
            Long connections = scalar(connection, connectionsSql());
            Long dataSize = scalar(connection, dataSizeSql());
            Long objects = scalar(connection, objectCountSql());
            return Optional.of(
                    new EngineMetrics(
                            connections == null ? null : connections.intValue(),
                            dataSize,
                            objects == null ? null : objects.intValue()));
        } catch (SQLException ex) {
            throw new EngineException("Metrics failed: " + ex.getMessage());
        }
    }

    /**
     * Runs a single-value numeric query, returning {@code null} when the SQL or the result is null.
     */
    private Long scalar(Connection connection, String sql) throws SQLException {
        if (sql == null) {
            return null;
        }
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                long value = rs.getLong(1);
                return rs.wasNull() ? null : value;
            }
            return null;
        }
    }

    private Connection connection(ConnectionDetails details) throws SQLException {
        return connectionPool.connection(jdbcUrl(details), details.username(), details.password());
    }

    @Override
    public SchemaInfo introspect(ConnectionDetails details) {
        try (Connection connection = connection(details)) {
            return doIntrospect(connection);
        } catch (SQLException ex) {
            throw new EngineException("Introspection failed: " + ex.getMessage());
        }
    }

    @Override
    public RowPage browse(ConnectionDetails details, ObjectRef ref, int offset, int limit) {
        try (Connection connection = connection(details)) {
            return doBrowse(connection, ref, offset, limit);
        } catch (SQLException ex) {
            throw new EngineException("Browse failed: " + ex.getMessage());
        }
    }

    @Override
    public QueryResult runQuery(ConnectionDetails details, String sql, QueryMode mode) {
        try (Connection connection = connection(details)) {
            return mode == QueryMode.READ
                    ? runReadOnly(connection, sql)
                    : runWritable(connection, sql);
        } catch (SQLException ex) {
            throw new EngineException("Query failed: " + ex.getMessage());
        }
    }

    private SchemaInfo doIntrospect(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<TableInfo> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[] {"TABLE", "VIEW"})) {
            while (rs.next()) {
                String schema =
                        databaseIsCatalog()
                                ? rs.getString("TABLE_CAT")
                                : rs.getString("TABLE_SCHEM");
                if (schema == null || systemSchemas().contains(schema)) {
                    continue;
                }
                String name = rs.getString("TABLE_NAME");
                tables.add(
                        new TableInfo(
                                schema,
                                name,
                                rs.getString("TABLE_TYPE"),
                                columns(meta, schema, name)));
            }
        }
        return new SchemaInfo(tables);
    }

    private List<ColumnInfo> columns(DatabaseMetaData meta, String schema, String table)
            throws SQLException {
        Set<String> primaryKeys = primaryKeys(meta, schema, table);
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = getColumns(meta, schema, table)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                columns.add(
                        new ColumnInfo(
                                column,
                                rs.getString("TYPE_NAME"),
                                "YES".equals(rs.getString("IS_NULLABLE")),
                                primaryKeys.contains(column)));
            }
        }
        return columns;
    }

    private Set<String> primaryKeys(DatabaseMetaData meta, String schema, String table)
            throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet rs = getPrimaryKeys(meta, schema, table)) {
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return keys;
    }

    private ResultSet getColumns(DatabaseMetaData meta, String schema, String table)
            throws SQLException {
        return databaseIsCatalog()
                ? meta.getColumns(schema, null, table, "%")
                : meta.getColumns(null, schema, table, "%");
    }

    private ResultSet getPrimaryKeys(DatabaseMetaData meta, String schema, String table)
            throws SQLException {
        return databaseIsCatalog()
                ? meta.getPrimaryKeys(schema, null, table)
                : meta.getPrimaryKeys(null, schema, table);
    }

    private RowPage doBrowse(Connection connection, ObjectRef ref, int offset, int limit)
            throws SQLException {
        String sql = "SELECT * FROM " + qualified(ref) + " LIMIT ? OFFSET ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, limit));
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                List<String> columns = columnNames(rs);
                return new RowPage(columns, readRows(rs, columns.size(), limit), offset, limit);
            }
        }
    }

    /**
     * Runs the query in an explicit read-only transaction. JDBC drivers typically only enforce
     * read-only within a transaction, so writes issued here are rejected by the server. The
     * transaction is rolled back (nothing to commit).
     */
    private QueryResult runReadOnly(Connection connection, String sql) throws SQLException {
        boolean priorAutoCommit = connection.getAutoCommit();
        boolean priorReadOnly = connection.isReadOnly();
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        try (Statement statement = connection.createStatement()) {
            QueryResult result = execute(statement, sql);
            connection.rollback();
            return result;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setReadOnly(priorReadOnly);
            connection.setAutoCommit(priorAutoCommit);
        }
    }

    private QueryResult runWritable(Connection connection, String sql) throws SQLException {
        boolean priorReadOnly = connection.isReadOnly();
        connection.setReadOnly(false);
        try (Statement statement = connection.createStatement()) {
            return execute(statement, sql);
        } finally {
            connection.setReadOnly(priorReadOnly);
        }
    }

    private QueryResult execute(Statement statement, String sql) throws SQLException {
        boolean hasResultSet = statement.execute(sql);
        if (hasResultSet) {
            try (ResultSet rs = statement.getResultSet()) {
                List<String> columns = columnNames(rs);
                return QueryResult.ofRows(columns, readRows(rs, columns.size(), MAX_QUERY_ROWS));
            }
        }
        return QueryResult.ofUpdate(statement.getUpdateCount());
    }

    private String qualified(ObjectRef ref) {
        return quote(ref.schema()) + "." + quote(ref.name());
    }

    private List<String> columnNames(ResultSet rs) throws SQLException {
        int count = rs.getMetaData().getColumnCount();
        List<String> names = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            names.add(rs.getMetaData().getColumnLabel(i));
        }
        return names;
    }

    private List<List<Object>> readRows(ResultSet rs, int columnCount, int maxRows)
            throws SQLException {
        List<List<Object>> rows = new ArrayList<>();
        while (rs.next() && rows.size() < maxRows) {
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
