package com.janne6565.stratabackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Links an external provider identity (e.g. Authentik subject) to a local {@link UserEntity}. Maps
 * the {@code oauth_identity} table. The {@code (provider, provider_subject)} pair is unique — one
 * external account maps to at most one local user. Email is stored here (never on {@code
 * app_user}).
 */
@Entity
@Table(
        name = "oauth_identity",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_oauth_identity",
                        columnNames = {"provider", "provider_subject"}))
@Getter
@Setter
@NoArgsConstructor
public class OAuthIdentityEntity {

    @Id private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OAuthIdentityEntity(
            UserEntity user, String provider, String providerSubject, String email) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
        this.createdAt = Instant.now();
    }
}
