package com.janne6565.stratabackend.repository;

import com.janne6565.stratabackend.entity.OAuthIdentityEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentityEntity, UUID> {

    Optional<OAuthIdentityEntity> findByProviderAndProviderSubject(
            String provider, String providerSubject);
}
