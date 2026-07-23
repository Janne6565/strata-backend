package com.janne6565.stratabackend.services.discovery;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.AutoScan;
import com.janne6565.stratabackend.model.core.DiscoverySummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoveryScanSchedulerTest {

    private static final String HOURLY_CRON = "0 0 * * * *";

    @Mock private DiscoveryService discoveryService;

    private DiscoveryScanScheduler schedulerWith(boolean enabled) {
        DiscoveryProperties props =
                new DiscoveryProperties(List.of(), new AutoScan(enabled, HOURLY_CRON));
        return new DiscoveryScanScheduler(discoveryService, props);
    }

    @Test
    void runsRescanWhenEnabled() {
        when(discoveryService.rescan()).thenReturn(new DiscoverySummary(0, 0, 0, 0));

        assertThatCode(() -> schedulerWith(true).scheduledRescan()).doesNotThrowAnyException();

        verify(discoveryService).rescan();
    }

    @Test
    void skipsRescanWhenDisabled() {
        schedulerWith(false).scheduledRescan();

        verify(discoveryService, never()).rescan();
    }

    @Test
    void swallowsAndLogsExceptionFromRescan() {
        when(discoveryService.rescan()).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> schedulerWith(true).scheduledRescan()).doesNotThrowAnyException();

        verify(discoveryService).rescan();
    }
}
