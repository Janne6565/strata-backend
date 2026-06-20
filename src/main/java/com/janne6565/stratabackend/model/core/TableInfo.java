package com.janne6565.stratabackend.model.core;

import java.util.List;

/**
 * A table (or view) discovered during introspection. {@code rowCount} is an approximate row count
 * sourced from the engine's statistics (cheap but not exact), or {@code null} when the engine can't
 * estimate it.
 */
public record TableInfo(
        String schema, String name, String type, List<ColumnInfo> columns, Long rowCount) {}
