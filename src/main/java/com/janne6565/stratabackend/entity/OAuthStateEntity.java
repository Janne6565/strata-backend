package com.janne6565.stratabackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single-use OAuth {@code state} value for CSRF protection of the authorization-code flow. Maps
 * the {@code oauth_state} table; rows are consumed on callback and evicted once past their TTL.
 */
@Entity
@Table(name = "oauth_state")
@Getter
@Setter
@NoArgsConstructor
public class OAuthStateEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 64)
    private String state;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    public OAuthStateEntity(String state, Instant issuedAt) {
        this.state = state;
        this.issuedAt = issuedAt;
    }
}
