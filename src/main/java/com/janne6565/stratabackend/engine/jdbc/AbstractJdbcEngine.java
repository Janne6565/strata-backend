package com.janne6565.stratabackend.engine.jdbc;

import com.janne6565.stratabackend.engine.ColumnInfo;
import com.janne6565.stratabackend.engine.DatabaseEngine;
import com.janne6565.stratabackend.engine.ObjectRef;
import com.janne6565.stratabackend.engine.QueryMode;
import com.janne6565.stratabackend.engine.QueryResult;
import com.janne6565.stratabackend.engine.RowPage;
import com.janne6565.stratabackend.engine.SchemaInfo;
import com.janne6565.stratabackend.engine.TableInfo;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared JDBC implementation of the engine SPI: metadata introspection, paged browse and
 * read-only-transaction query execution. Subclasses supply the engine-specific bits — driver id,
 * identifier quoting, system schemas, and whether the database name lives in the JDBC catalog
 * (MySQL) or schema (PostgreSQL).
 */
public abstract class AbstractJdbcEngine implements DatabaseEngine {

    /** Cap on rows materialised from an ad-hoc query result, to bound memory. */
    protected static final int MAX_QUERY_ROWS = 1000;

    /** Schemas/catalogs to hide from introspection. */
    protected abstract Set<String> systemSchemas();

    /** Quotes an identifier for this dialect. */
    protected abstract String quote(String identifier);

    /** True when the database name is the JDBC catalog (MySQL) rather than the schema (PostgreSQL). */
    protected abstract boolean databaseIsCatalog();

    @Override
    public boolean canEnforceReadOnly() {
        return true;
    }

    @Override
    public SchemaInfo introspect(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<TableInfo> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[] {"TABLE", "VIEW"})) {
            while (rs.next()) {
                String schema = databaseIsCatalog() ? rs.getString("TABLE_CAT") : rs.getString("TABLE_SCHEM");
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

    @Override
    public RowPage browse(Connection connection, ObjectRef ref, int offset, int limit)
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

    @Override
    public QueryResult runQuery(Connection connection, String sql, QueryMode mode)
            throws SQLException {
        return mode == QueryMode.READ ? runReadOnly(connection, sql) : runWritable(connection, sql);
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
