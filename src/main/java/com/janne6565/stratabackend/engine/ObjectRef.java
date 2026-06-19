package com.janne6565.stratabackend.engine;

/** Identifies a browsable object (a table/view) within a database. */
public record ObjectRef(String schema, String name) {}
