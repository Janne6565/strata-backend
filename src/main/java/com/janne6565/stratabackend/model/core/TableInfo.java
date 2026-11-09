package com.janne6565.stratabackend.model.core;

import java.util.List;

/**
 * A table (or view) discovered during introspection. {@code rowCount} is an approximate row count
 * sourced from the engine's statistics (cheap but not exact), or {@code null} when the engine can't
 * estimate it. {@code sizeBytes} is the object's storage footprint where the engine reports one
 * (e.g. a MinIO bucket's total object size), otherwise {@code null}.
 */
public record TableInfo(
        String schema,
        String name,
        String type,
        List<ColumnInfo> columns,
        Long rowCount,
        Long sizeBytes) {

    /** A table without a known storage size. */
    public TableInfo(
            String schema, String name, String type, List<ColumnInfo> columns, Long rowCount) {
        this(schema, name, type, columns, rowCount, null);
    }
}
