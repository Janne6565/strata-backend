package com.janne6565.stratabackend.engine;

import java.util.List;

/**
 * A page of rows. {@code columns} are the column names; each row is a positional list of cell
 * values (JSON-serialisable). {@code offset}/{@code limit} echo the request.
 */
public record RowPage(List<String> columns, List<List<Object>> rows, int offset, int limit) {}
