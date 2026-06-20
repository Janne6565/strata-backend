package com.janne6565.stratabackend.model.core;

import com.janne6565.stratabackend.model.exception.BadRequestException;

/**
 * A comparison operator for a browse column filter, paired with its SQL rendering. Values are bound
 * as JDBC parameters; only the (fixed) operator and the validated column name reach the SQL string.
 */
public enum FilterOp {
    EQ("="),
    NE("<>"),
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    LIKE("LIKE"),
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL");

    private final String sql;

    FilterOp(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }

    /** Whether this operator binds a value (false for the null checks). */
    public boolean needsValue() {
        return this != IS_NULL && this != IS_NOT_NULL;
    }

    /** Maps a wire token (e.g. {@code gte}, {@code isnull}) to an operator. */
    public static FilterOp fromToken(String token) {
        return switch (token == null ? "" : token.toLowerCase()) {
            case "eq" -> EQ;
            case "ne" -> NE;
            case "lt" -> LT;
            case "lte" -> LTE;
            case "gt" -> GT;
            case "gte" -> GTE;
            case "like" -> LIKE;
            case "isnull" -> IS_NULL;
            case "isnotnull" -> IS_NOT_NULL;
            default -> throw new BadRequestException("Unknown filter operator: " + token);
        };
    }
}
