package com.janne6565.stratabackend.model.action;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin request to manually register a datasource, anchored on a workload (ARCHITECTURE.md §8).
 * The discovery key is derived from namespace/kind/name so a later scan can reconcile to this row.
 */
public record ManualAddRequest(
        @NotBlank String namespace,
        String workloadKind,
        String workloadName,
        String serviceName,
        Integer servicePort,
        @NotBlank String driver,
        @NotBlank String displayName,
        boolean readOnlyCapability) {}
