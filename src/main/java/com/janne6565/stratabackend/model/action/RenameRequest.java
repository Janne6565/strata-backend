package com.janne6565.stratabackend.model.action;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin request to rename a datasource — sets a human-friendly display name (e.g. "Cosy Database")
 * over the discovered workload name. The new name is recorded as a manual override so a later
 * rescan won't clobber it.
 */
public record RenameRequest(@NotBlank @Size(max = 255) String displayName) {}
