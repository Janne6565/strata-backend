package com.janne6565.stratabackend.model.core;

/** A column in an introspected table. */
public record ColumnInfo(String name, String type, boolean nullable, boolean primaryKey) {}
