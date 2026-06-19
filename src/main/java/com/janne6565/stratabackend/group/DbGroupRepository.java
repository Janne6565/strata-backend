package com.janne6565.stratabackend.group;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DbGroupRepository extends JpaRepository<DbGroup, UUID> {

    List<DbGroup> findByOwnerUserIdOrderByPositionAsc(UUID ownerUserId);

    long countByOwnerUserId(UUID ownerUserId);
}
