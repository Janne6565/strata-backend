package com.janne6565.stratabackend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A per-user logical grouping of datasources (ARCHITECTURE.md §4). Groups are labels only — they
 * never touch the cluster — and are private to their owner. Membership maps the
 * {@code db_group_member} join table as a set of datasource ids.
 */
@Entity
@Table(name = "db_group")
@Getter
@Setter
@NoArgsConstructor
public class DbGroupEntity {

    @Id private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false)
    private String name;

    /** Sort position within the owner's group list (ascending). */
    @Column(nullable = false)
    private int position;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "db_group_member", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "datasource_id", nullable = false)
    private Set<UUID> datasourceIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
