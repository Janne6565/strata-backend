package com.janne6565.stratabackend.model.core;

/** The outcome of matching a workload's image against the detector config. */
public record DetectorMatch(String detectorId, String driver, Confidence confidence) {}
