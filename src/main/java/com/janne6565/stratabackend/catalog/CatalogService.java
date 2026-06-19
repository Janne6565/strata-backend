package com.janne6565.stratabackend.catalog;

import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.dto.DatasourceResponse;
import com.janne6565.stratabackend.catalog.dto.ManualAddRequest;
import com.janne6565.stratabackend.common.ConflictException;
import com.janne6565.stratabackend.common.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Read and manual-management of the datasource catalog (scan-driven changes live in discovery). */
@Service
public class CatalogService {

    private final DatasourceRepository datasourceRepository;

    public CatalogService(DatasourceRepository datasourceRepository) {
        this.datasourceRepository = datasourceRepository;
    }

    @Transactional(readOnly = true)
    public List<DatasourceResponse> list() {
        return datasourceRepository.findAll().stream().map(DatasourceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DatasourceResponse get(UUID id) {
        return datasourceRepository
                .findById(id)
                .map(DatasourceResponse::from)
                .orElseThrow(() -> new NotFoundException("Datasource not found: " + id));
    }

    @Transactional
    public DatasourceResponse manualAdd(ManualAddRequest request, User createdBy) {
        String discoveryKey = discoveryKey(request);
        if (datasourceRepository.existsByDiscoveryKey(discoveryKey)) {
            throw new ConflictException("A datasource already exists for " + discoveryKey);
        }
        Instant now = Instant.now();
        Datasource ds = new Datasource();
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

    @Transactional
    public void unregister(UUID id) {
        if (!datasourceRepository.existsById(id)) {
            throw new NotFoundException("Datasource not found: " + id);
        }
        datasourceRepository.deleteById(id);
    }

    private String discoveryKey(ManualAddRequest request) {
        String kind = StringUtils.hasText(request.workloadKind()) ? request.workloadKind() : "manual";
        String name =
                StringUtils.hasText(request.workloadName())
                        ? request.workloadName()
                        : request.displayName();
        return request.namespace() + "/" + kind + "/" + name;
    }
}
