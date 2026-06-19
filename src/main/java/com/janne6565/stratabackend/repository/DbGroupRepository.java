package com.janne6565.stratabackend.repository;

import com.janne6565.stratabackend.entity.DbGroupEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DbGroupRepository extends JpaRepository<DbGroupEntity, UUID> {

    List<DbGroupEntity> findByOwnerUserIdOrderByPositionAsc(UUID ownerUserId);

    long countByOwnerUserId(UUID ownerUserId);
}
