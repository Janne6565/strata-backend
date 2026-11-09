package com.janne6565.stratabackend.services.engine;

import com.janne6565.stratabackend.model.core.ConnectionDetails;

/**
 * Optional capability of a {@link DatabaseEngine} that stores files (e.g. MinIO): reading one
 * object's bytes, for preview and download. Read-only by design.
 */
public interface ObjectStorageEngine {

    /**
     * Opens {@code key} in {@code bucket} for streaming.
     *
     * @throws com.janne6565.stratabackend.model.exception.EngineException if it can't be read
     */
    StoredObject openObject(ConnectionDetails details, String bucket, String key);
}
