package com.janne6565.stratabackend.model.core;

/** Sort direction for a browse query. */
public enum SortDirection {
    ASC,
    DESC;

    /** Lenient parse: {@code desc} (any case) is DESC; everything else (including null) is ASC. */
    public static SortDirection fromString(String value) {
        return value != null && value.equalsIgnoreCase("desc") ? DESC : ASC;
    }
}
