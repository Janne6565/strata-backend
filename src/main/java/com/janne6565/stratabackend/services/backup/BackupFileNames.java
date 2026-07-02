package com.janne6565.stratabackend.services.backup;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Naming scheme for backup files: {@code strata-backup-20260702T140000Z.dump}. The timestamp in the
 * name (UTC) is the source of truth for a backup's age — never file mtime, which copies and PVC
 * migrations would corrupt.
 */
public final class BackupFileNames {

    public static final String TMP_SUFFIX = ".tmp";

    private static final String PREFIX = "strata-backup-";
    private static final String EXTENSION = ".dump";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private BackupFileNames() {}

    public static String fileName(Instant createdAt) {
        return PREFIX
                + TIMESTAMP.format(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                + EXTENSION;
    }

    /** Empty for anything that is not a well-formed backup file name (including tmp files). */
    public static Optional<Instant> parse(String fileName) {
        if (!fileName.startsWith(PREFIX) || !fileName.endsWith(EXTENSION)) {
            return Optional.empty();
        }
        String timestamp =
                fileName.substring(PREFIX.length(), fileName.length() - EXTENSION.length());
        try {
            return Optional.of(LocalDateTime.parse(timestamp, TIMESTAMP).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
