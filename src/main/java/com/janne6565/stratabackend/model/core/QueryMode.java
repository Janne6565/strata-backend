package com.janne6565.stratabackend.model.core;


/** Whether a query is allowed to mutate. READ runs on a read-only connection (defence-in-depth). */
public enum QueryMode {
    READ,
    WRITE
}
