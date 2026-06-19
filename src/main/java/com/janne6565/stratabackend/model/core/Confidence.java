package com.janne6565.stratabackend.model.core;


/**
 * How sure discovery is about a detector match (ARCHITECTURE.md §8). An image-regex match alone is
 * {@code MEDIUM}; corroboration from an expected port raises it to {@code HIGH}. Stored in
 * {@code datasource.detection_confidence}.
 */
public enum Confidence {
    LOW,
    MEDIUM,
    HIGH
}
