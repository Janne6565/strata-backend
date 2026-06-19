package com.janne6565.stratabackend.repository;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceRepository extends JpaRepository<DatasourceEntity, UUID> {

    Optional<DatasourceEntity> findByDiscoveryKey(String discoveryKey);

    boolean existsByDiscoveryKey(String discoveryKey);
}
