package com.janne6565.stratabackend.configuration.backup;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling (currently used only by the backup job). {@code @Scheduled} fires in
 * every replica: the deployment runs a single backend replica, so this is safe as-is. If the
 * backend is ever scaled out, guard scheduled jobs with a distributed lock (e.g. ShedLock) first.
 */
@Configuration
@EnableScheduling
public class BackupConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
