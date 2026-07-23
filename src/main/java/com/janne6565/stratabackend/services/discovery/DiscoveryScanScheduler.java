package com.janne6565.stratabackend.services.discovery;

import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties;
import com.janne6565.stratabackend.model.core.DiscoverySummary;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the cluster re-scan periodically, invoking the exact same {@link DiscoveryService#rescan()}
 * the manual {@code /rescan} endpoint uses so there is no duplicated discovery logic. The schedule
 * is always registered; when {@code strata.discovery.auto-scan.enabled} is false the run is
 * skipped. Scheduling is enabled globally via {@code BackupConfig}.
 *
 * <p>{@code rescan()} is not itself guarded against concurrent invocation, so an in-flight flag
 * prevents a slow scheduled scan from overlapping the next tick; the manual endpoint is unaffected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryScanScheduler {

    private final DiscoveryService discoveryService;
    private final DiscoveryProperties discoveryProperties;
    private final AtomicBoolean scanInFlight = new AtomicBoolean(false);

    @Scheduled(cron = "${strata.discovery.auto-scan.cron}")
    public void scheduledRescan() {
        if (!discoveryProperties.autoScan().enabled()) {
            log.debug("Discovery auto-scan disabled; skipping scheduled rescan");
            return;
        }
        if (!scanInFlight.compareAndSet(false, true)) {
            log.warn("Previous discovery auto-scan still running; skipping this scheduled rescan");
            return;
        }
        try {
            DiscoverySummary summary = discoveryService.rescan();
            log.debug("Scheduled discovery rescan completed: {}", summary);
        } catch (Exception e) {
            log.error("Scheduled discovery rescan failed", e);
        } finally {
            scanInFlight.set(false);
        }
    }
}
