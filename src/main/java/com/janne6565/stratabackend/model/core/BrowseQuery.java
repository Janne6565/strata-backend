package com.janne6565.stratabackend.model.core;

import java.util.List;

/**
 * Parameters for a paged table browse: the paging window plus optional ordering and column filters.
 * Sort/filter are honoured by relational (JDBC) engines; engines that can't express them ignore the
 * extra fields (see {@code DatabaseEngine#browse(ConnectionDetails, ObjectRef, BrowseQuery)}).
 */
public record BrowseQuery(
        int offset,
        int limit,
        String orderBy,
        SortDirection direction,
        List<ColumnFilter> filters) {

    public BrowseQuery {
        direction = direction == null ? SortDirection.ASC : direction;
        filters = filters == null ? List.of() : List.copyOf(filters);
    }

    /** A plain paging query with no ordering or filters. */
    public static BrowseQuery paged(int offset, int limit) {
        return new BrowseQuery(offset, limit, null, SortDirection.ASC, List.of());
    }

    public boolean hasOrder() {
        return orderBy != null && !orderBy.isBlank();
    }

    /** Returns a copy with a replaced limit (used to clamp to the engine's cap). */
    public BrowseQuery withLimit(int newLimit) {
        return new BrowseQuery(offset, newLimit, orderBy, direction, filters);
    }
}
