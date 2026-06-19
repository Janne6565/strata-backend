package com.janne6565.stratabackend.model.core;


/** Lifecycle status reconciled against the live cluster: still present, or gone since last seen. */
public enum DatasourceStatus {
    PRESENT,
    MISSING
}
