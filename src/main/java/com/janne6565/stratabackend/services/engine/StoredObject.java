package com.janne6565.stratabackend.services.engine;

import java.io.IOException;
import java.io.InputStream;

/**
 * An object's bytes streamed from an object store, with the metadata needed to serve it. {@code
 * contentType} is whatever the uploader stored (untrusted, may be null) and {@code sizeBytes} is -1
 * when unknown. The caller must close it.
 */
public record StoredObject(String contentType, long sizeBytes, InputStream content)
        implements AutoCloseable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}
