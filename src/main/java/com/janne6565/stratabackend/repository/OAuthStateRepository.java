package com.janne6565.stratabackend.repository;

import com.janne6565.stratabackend.entity.OAuthStateEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OAuthStateRepository extends JpaRepository<OAuthStateEntity, String> {

    @Modifying
    @Query("DELETE FROM OAuthStateEntity s WHERE s.issuedAt < :expiredBefore")
    void deleteExpiredBefore(Instant expiredBefore);
}
