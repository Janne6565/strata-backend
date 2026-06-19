package com.janne6565.stratabackend.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A database Strata knows about — the stable, persisted identity that grants/groups/audit
 * reference (ARCHITECTURE.md §4). This M1 mapping covers the scalar identity/lifecycle columns;
 * M2 (discovery) adds the jsonb credential-resolution / manual-overrides fields and the scan
 * reconciliation. {@code ddl-auto: validate} tolerates the as-yet-unmapped columns.
 */
@Entity
@Table(name = "datasource")
@Getter
@Setter
@NoArgsConstructor
public class Datasource {

    @Id private UUID id;

    @Column(name = "discovery_key", nullable = false, unique = true)
    private String discoveryKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DatasourceOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DatasourceStatus status;

    @Column(nullable = false)
    private String namespace;

    @Column(name = "workload_kind")
    private String workloadKind;

    @Column(name = "workload_name")
    private String workloadName;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_port")
    private Integer servicePort;

    @Column(nullable = false)
    private String driver;

    @Column(name = "engine_version")
    private String engineVersion;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "read_only_capability", nullable = false)
    private boolean readOnlyCapability;

    @Column(name = "detection_confidence", length = 16)
    private String detectionConfidence;

    @Column(name = "detected_via")
    private String detectedVia;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
