package com.janne6565.stratabackend.model.core;

/** How a datasource entered the catalog: auto-discovered by a scan, or added by an admin. */
public enum DatasourceOrigin {
    DISCOVERED,
    MANUAL
}
