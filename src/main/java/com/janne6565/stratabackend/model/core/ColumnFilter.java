package com.janne6565.stratabackend.model.core;

/** A single column predicate in a browse query: {@code column op value}. */
public record ColumnFilter(String column, FilterOp op, String value) {}
