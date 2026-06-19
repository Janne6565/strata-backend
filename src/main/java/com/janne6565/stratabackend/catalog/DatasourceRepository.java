package com.janne6565.stratabackend.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceRepository extends JpaRepository<Datasource, UUID> {

    Optional<Datasource> findByDiscoveryKey(String discoveryKey);

    boolean existsByDiscoveryKey(String discoveryKey);
}
