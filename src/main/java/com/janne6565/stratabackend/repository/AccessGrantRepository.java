package com.janne6565.stratabackend.repository;

import com.janne6565.stratabackend.entity.AccessGrantEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGrantRepository extends JpaRepository<AccessGrantEntity, UUID> {

    List<AccessGrantEntity> findByUserId(UUID userId);
}
