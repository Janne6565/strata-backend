package com.janne6565.stratabackend.services.core;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.ManualAddRequest;
import com.janne6565.stratabackend.model.core.DatasourceOrigin;
import com.janne6565.stratabackend.model.core.DatasourceResponse;
import com.janne6565.stratabackend.model.core.DatasourceStatus;
import com.janne6565.stratabackend.model.exception.ConflictException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Read and manual-management of the datasource catalog (scan-driven changes live in discovery). */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final DatasourceRepository datasourceRepository;
    private final GrantEvaluator grantEvaluator;

    /** Service-layer scoping (enforcement layer 2): callers see only datasources they may read. */
    @Transactional(readOnly = true)
    public List<DatasourceResponse> list(UserEntity caller) {
        return datasourceRepository.findAll().stream()
                .filter(ds -> grantEvaluator.canRead(caller, ds))
                .map(DatasourceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasourceResponse get(UUID id) {
        return datasourceRepository
                .findById(id)
                .map(DatasourceResponse::from)
                .orElseThrow(() -> new NotFoundException("DatasourceEntity not found: " + id));
    }

    @Transactional
    public DatasourceResponse manualAdd(ManualAddRequest request, UserEntity createdBy) {
        String discoveryKey = discoveryKey(request);
        if (datasourceRepository.existsByDiscoveryKey(discoveryKey)) {
            throw new ConflictException("A datasource already exists for " + discoveryKey);
        }
        Instant now = Instant.now();
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(UUID.randomUUID());
        ds.setDiscoveryKey(discoveryKey);
        ds.setOrigin(DatasourceOrigin.MANUAL);
        ds.setStatus(DatasourceStatus.PRESENT);
        ds.setNamespace(request.namespace());
        ds.setWorkloadKind(request.workloadKind());
        ds.setWorkloadName(request.workloadName());
        ds.setServiceName(request.serviceName());
        ds.setServicePort(request.servicePort());
        ds.setDriver(request.driver());
        ds.setDisplayName(request.displayName());
        ds.setReadOnlyCapability(request.readOnlyCapability());
        ds.setCreatedBy(createdBy.getId());
        ds.setFirstSeenAt(now);
        ds.setLastSeenAt(now);
        ds.setCreatedAt(now);
        return DatasourceResponse.from(datasourceRepository.save(ds));
    }

    /**
     * Renames a datasource's display name and records it as a manual override, so a later rescan
     * preserves it (discovery only refreshes unlocked fields).
     */
    @Transactional
    public DatasourceResponse rename(UUID id, String displayName) {
        DatasourceEntity ds =
                datasourceRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new NotFoundException("DatasourceEntity not found: " + id));
        ds.setDisplayName(displayName);
        Map<String, String> overrides =
                ds.getManualOverrides() == null
                        ? new HashMap<>()
                        : new HashMap<>(ds.getManualOverrides());
        overrides.put("displayName", displayName);
        ds.setManualOverrides(overrides);
        return DatasourceResponse.from(datasourceRepository.save(ds));
    }

    @Transactional
    public void unregister(UUID id) {
        if (!datasourceRepository.existsById(id)) {
            throw new NotFoundException("DatasourceEntity not found: " + id);
        }
        datasourceRepository.deleteById(id);
    }

    private String discoveryKey(ManualAddRequest request) {
        String kind =
                StringUtils.hasText(request.workloadKind()) ? request.workloadKind() : "manual";
        String name =
                StringUtils.hasText(request.workloadName())
                        ? request.workloadName()
                        : request.displayName();
        return request.namespace() + "/" + kind + "/" + name;
    }
}
