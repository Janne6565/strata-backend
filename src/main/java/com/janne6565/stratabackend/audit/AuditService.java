package com.janne6565.stratabackend.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail. Each record commits in its own transaction (REQUIRES_NEW) so a failure
 * being audited still leaves a trace even when the surrounding work rolls back. Query summaries are
 * truncated and must never contain secret values (AUTH.md).
 */
@Service
public class AuditService {

    private static final int MAX_SUMMARY = 2000;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID userId,
            String operation,
            String targetRef,
            String namespace,
            AuditOutcome outcome,
            String querySummary) {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID());
        entry.setUserId(userId);
        entry.setOperation(operation);
        entry.setTargetRef(targetRef);
        entry.setNamespace(namespace);
        entry.setOutcome(outcome);
        entry.setQuerySummary(truncate(querySummary));
        entry.setAt(Instant.now());
        repository.save(entry);
    }

    private String truncate(String summary) {
        if (summary == null || summary.length() <= MAX_SUMMARY) {
            return summary;
        }
        return summary.substring(0, MAX_SUMMARY);
    }
}
