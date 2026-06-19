package com.janne6565.stratabackend.model.core;

import java.util.List;

/** A table (or view) discovered during introspection. */
public record TableInfo(String schema, String name, String type, List<ColumnInfo> columns) {}
