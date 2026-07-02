package com.janne6565.stratabackend.model.core;

import java.time.Instant;

/** Public view of a stored database backup (metadata only; the bytes are fetched via download). */
public record BackupResponse(String name, long sizeBytes, Instant createdAt) {}
