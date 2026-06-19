package com.janne6565.stratabackend.repository;

import com.janne6565.stratabackend.entity.AuditLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {}
