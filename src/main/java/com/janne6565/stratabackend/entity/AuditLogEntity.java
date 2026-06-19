package com.janne6565.stratabackend.entity;

import com.janne6565.stratabackend.model.core.AuditOutcome;
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
 * Audit trail entry: who did what, to which resource, with what outcome (ARCHITECTURE.md §4).
 * {@code querySummary} stores only a summary — never raw secrets or full sensitive payloads.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntity {

    @Id private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(name = "target_ref", length = 512)
    private String targetRef;

    @Column private String namespace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditOutcome outcome;

    @Column(name = "query_summary")
    private String querySummary;

    @Column(name = "at", nullable = false)
    private Instant at;
}
