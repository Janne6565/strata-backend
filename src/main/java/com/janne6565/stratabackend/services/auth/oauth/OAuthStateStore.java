package com.janne6565.stratabackend.services.auth.oauth;

import com.janne6565.stratabackend.entity.OAuthStateEntity;
import com.janne6565.stratabackend.repository.OAuthStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use OAuth {@code state} values (CSRF protection for the
 * authorization-code flow). A state is valid for {@link #STATE_TTL} and can be consumed exactly
 * once; expired rows are swept hourly. Scheduling is enabled globally via {@code BackupConfig}.
 */
@Component
@RequiredArgsConstructor
public class OAuthStateStore {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final OAuthStateRepository repository;

    /** Generates, persists and returns a fresh state token. */
    public String issueState() {
        String state = UUID.randomUUID().toString();
        repository.save(new OAuthStateEntity(state, Instant.now()));
        return state;
    }

    /**
     * Consumes a state: deletes it (single-use) and reports whether it existed and is still within
     * its TTL. Public so Spring's {@code @Transactional} proxy applies.
     */
    @Transactional
    public boolean consumeState(String state) {
        Optional<OAuthStateEntity> entity = repository.findById(state);
        if (entity.isEmpty()) {
            return false;
        }
        repository.deleteById(state);
        return entity.get().getIssuedAt().plus(STATE_TTL).isAfter(Instant.now());
    }

    @Scheduled(fixedRateString = "PT1H")
    @Transactional
    public void evictExpired() {
        repository.deleteExpiredBefore(Instant.now().minus(STATE_TTL));
    }
}
