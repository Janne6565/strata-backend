package com.janne6565.stratabackend.services.backup;

/** A backup run failed (pg_dump error, timeout, or file-system problem). */
public class BackupException extends RuntimeException {

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
