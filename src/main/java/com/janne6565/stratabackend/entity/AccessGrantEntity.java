package com.janne6565.stratabackend.entity;

import com.janne6565.stratabackend.model.core.ScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Grants a user access to a scope (a namespace or a single datasource) with an optional read-only
 * flag (ARCHITECTURE.md §4/§6). The DB enforces scope consistency via a CHECK constraint; the
 * service validates it up front to return a clean 400.
 */
@Entity
@Table(name = "access_grant")
@Getter
@Setter
@NoArgsConstructor
public class AccessGrantEntity {

    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private ScopeType scopeType;

    @Column(name = "namespace")
    private String namespace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id")
    private DatasourceEntity datasource;

    @Column(name = "read_only", nullable = false)
    private boolean readOnly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AccessGrantEntity(
            UserEntity user,
            ScopeType scopeType,
            String namespace,
            DatasourceEntity datasource,
            boolean readOnly,
            UserEntity createdBy) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.scopeType = scopeType;
        this.namespace = namespace;
        this.datasource = datasource;
        this.readOnly = readOnly;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
