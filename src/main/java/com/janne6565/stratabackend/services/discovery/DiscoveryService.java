package com.janne6565.stratabackend.services.discovery;
import lombok.extern.slf4j.Slf4j;

import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.Detector;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.CredentialResolution;
import com.janne6565.stratabackend.model.core.DatasourceOrigin;
import com.janne6565.stratabackend.model.core.DatasourceStatus;
import com.janne6565.stratabackend.model.core.DetectorMatch;
import com.janne6565.stratabackend.model.core.DiscoverySummary;
import com.janne6565.stratabackend.model.core.WorkloadDescriptor;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.services.kubernetes.CredentialResolver;
import com.janne6565.stratabackend.services.kubernetes.KubernetesScanner;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scans the cluster and reconciles the result against the datasource catalog (ARCHITECTURE.md §8):
 * matched workloads are upserted as PRESENT (refreshing mutable fields except those locked by
 * manual_overrides); rows not seen this scan become MISSING (never deleted, so the UI can show
 * "gone, last seen …" and grants keep their FK).
 */
@Slf4j
@Service
public class DiscoveryService {


    private final KubernetesScanner scanner;
    private final DetectorMatcher detectorMatcher;
    private final CredentialResolver credentialResolver;
    private final DatasourceRepository datasourceRepository;
    private final Map<String, Detector> detectorsById;

    public DiscoveryService(
            KubernetesScanner scanner,
            DetectorMatcher detectorMatcher,
            CredentialResolver credentialResolver,
            DatasourceRepository datasourceRepository,
            DiscoveryProperties discoveryProperties) {
        this.scanner = scanner;
        this.detectorMatcher = detectorMatcher;
        this.credentialResolver = credentialResolver;
        this.datasourceRepository = datasourceRepository;
        this.detectorsById =
                discoveryProperties.detectors().stream()
                        .collect(java.util.stream.Collectors.toMap(Detector::id, d -> d));
    }

    @Transactional
    public DiscoverySummary rescan() {
        Instant now = Instant.now();
        Set<String> seen = new HashSet<>();
        int created = 0;
        int updated = 0;

        for (WorkloadDescriptor workload : scanner.scan()) {
            Optional<DetectorMatch> matched = detectorMatcher.match(workload.image(), workload.ports());
            if (matched.isEmpty()) {
                continue;
            }
            DetectorMatch match = matched.get();
            seen.add(workload.discoveryKey());
            CredentialResolution credentials = resolveCredentials(match, workload);

            Optional<DatasourceEntity> existing =
                    datasourceRepository.findByDiscoveryKey(workload.discoveryKey());
            if (existing.isPresent()) {
                refresh(existing.get(), workload, match, credentials, now);
                updated++;
            } else {
                datasourceRepository.save(newDiscovered(workload, match, credentials, now));
                created++;
            }
        }

        int markedMissing = markMissing(seen);
        log.info(
                "Discovery rescan: {} matched, {} new, {} updated, {} now missing",
                seen.size(),
                created,
                updated,
                markedMissing);
        return new DiscoverySummary(created, updated, markedMissing, seen.size());
    }

    private CredentialResolution resolveCredentials(DetectorMatch match, WorkloadDescriptor workload) {
        Detector detector = detectorsById.get(match.detectorId());
        if (detector == null
                || detector.credentials() == null
                || detector.credentials().env() == null
                || detector.credentials().env().isEmpty()) {
            return null;
        }
        String strategy =
                detector.credentials().strategy() == null
                        ? match.detectorId()
                        : detector.credentials().strategy();
        return credentialResolver.resolve(
                workload.primaryContainer(), detector.credentials().env(), strategy);
    }

    private DatasourceEntity newDiscovered(
            WorkloadDescriptor workload,
            DetectorMatch match,
            CredentialResolution credentials,
            Instant now) {
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(UUID.randomUUID());
        ds.setDiscoveryKey(workload.discoveryKey());
        ds.setOrigin(DatasourceOrigin.DISCOVERED);
        ds.setStatus(DatasourceStatus.PRESENT);
        ds.setNamespace(workload.namespace());
        ds.setWorkloadKind(workload.workloadKind());
        ds.setWorkloadName(workload.workloadName());
        ds.setServiceName(workload.serviceName());
        ds.setServicePort(workload.servicePort());
        ds.setDriver(match.driver());
        ds.setDisplayName(workload.workloadName());
        ds.setDetectionConfidence(match.confidence().name());
        ds.setDetectedVia(match.detectorId());
        ds.setCredentialResolution(credentials);
        ds.setFirstSeenAt(now);
        ds.setLastSeenAt(now);
        ds.setCreatedAt(now);
        return ds;
    }

    private void refresh(
            DatasourceEntity ds,
            WorkloadDescriptor workload,
            DetectorMatch match,
            CredentialResolution credentials,
            Instant now) {
        Set<String> locked =
                ds.getManualOverrides() == null ? Set.of() : ds.getManualOverrides().keySet();
        ds.setStatus(DatasourceStatus.PRESENT);
        ds.setLastSeenAt(now);
        if (!locked.contains("driver")) {
            ds.setDriver(match.driver());
        }
        if (!locked.contains("serviceName")) {
            ds.setServiceName(workload.serviceName());
        }
        if (!locked.contains("servicePort")) {
            ds.setServicePort(workload.servicePort());
        }
        ds.setDetectionConfidence(match.confidence().name());
        ds.setDetectedVia(match.detectorId());
        if (credentials != null && !locked.contains("credentialResolution")) {
            ds.setCredentialResolution(credentials);
        }
    }

    /** Any catalog row not matched this scan is marked MISSING (rows are retained, never deleted). */
    private int markMissing(Set<String> seen) {
        int count = 0;
        for (DatasourceEntity ds : datasourceRepository.findAll()) {
            if (!seen.contains(ds.getDiscoveryKey()) && ds.getStatus() != DatasourceStatus.MISSING) {
                ds.setStatus(DatasourceStatus.MISSING);
                count++;
            }
        }
        return count;
    }
}
