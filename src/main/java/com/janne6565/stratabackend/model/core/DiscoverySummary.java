package com.janne6565.stratabackend.model.core;


/** Outcome counts of a reconciliation pass. */
public record DiscoverySummary(int created, int updated, int markedMissing, int matched) {}
