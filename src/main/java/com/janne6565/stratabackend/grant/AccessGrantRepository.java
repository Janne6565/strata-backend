package com.janne6565.stratabackend.grant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    List<AccessGrant> findByUserId(UUID userId);
}
