package com.janne6565.stratabackend.model.core;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import java.time.Instant;
import java.util.UUID;

/** Public view of a catalog datasource (no resolved secret values — only resolution pointers). */
public record DatasourceResponse(
        UUID id,
        String discoveryKey,
        DatasourceOrigin origin,
        DatasourceStatus status,
        String namespace,
        String workloadKind,
        String workloadName,
        String serviceName,
        Integer servicePort,
        String driver,
        String engineVersion,
        String displayName,
        boolean readOnlyCapability,
        String detectionConfidence,
        String detectedVia,
        CredentialResolution credentialResolution,
        Instant firstSeenAt,
        Instant lastSeenAt) {

    public static DatasourceResponse from(DatasourceEntity d) {
        return new DatasourceResponse(
                d.getId(),
                d.getDiscoveryKey(),
                d.getOrigin(),
                d.getStatus(),
                d.getNamespace(),
                d.getWorkloadKind(),
                d.getWorkloadName(),
                d.getServiceName(),
                d.getServicePort(),
                d.getDriver(),
                d.getEngineVersion(),
                d.getDisplayName(),
                d.isReadOnlyCapability(),
                d.getDetectionConfidence(),
                d.getDetectedVia(),
                d.getCredentialResolution(),
                d.getFirstSeenAt(),
                d.getLastSeenAt());
    }
}
