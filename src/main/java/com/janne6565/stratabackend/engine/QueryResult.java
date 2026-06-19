package com.janne6565.stratabackend.engine;

import java.util.List;

/**
 * The result of running a query. A SELECT yields {@code columns} + {@code rows} and
 * {@code updateCount = -1}; a write yields an empty result set and the affected-row
 * {@code updateCount}.
 */
public record QueryResult(List<String> columns, List<List<Object>> rows, int updateCount) {

    public static QueryResult ofRows(List<String> columns, List<List<Object>> rows) {
        return new QueryResult(columns, rows, -1);
    }

    public static QueryResult ofUpdate(int updateCount) {
        return new QueryResult(List.of(), List.of(), updateCount);
    }
}
