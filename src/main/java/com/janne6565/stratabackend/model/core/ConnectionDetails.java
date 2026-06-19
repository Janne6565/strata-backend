package com.janne6565.stratabackend.model.core;


/**
 * Live connection parameters for a datasource, assembled from cluster DNS + on-demand resolved
 * credentials. In-memory only — never persisted or logged (AUTH.md).
 */
public record ConnectionDetails(
        String driver, String host, int port, String database, String username, String password) {}
