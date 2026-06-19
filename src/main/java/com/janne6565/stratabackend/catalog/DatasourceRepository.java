package com.janne6565.stratabackend.catalog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceRepository extends JpaRepository<Datasource, UUID> {}
