package com.janne6565.stratabackend.controller.v1.implementation;
import lombok.RequiredArgsConstructor;

import com.janne6565.stratabackend.controller.v1.schema.InventoryApi;
import com.janne6565.stratabackend.model.action.ManualAddRequest;
import com.janne6565.stratabackend.model.core.DatasourceResponse;
import com.janne6565.stratabackend.model.core.DiscoverySummary;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.security.authorization.ResourceId;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.core.CatalogService;
import com.janne6565.stratabackend.services.discovery.DiscoveryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/** Implements {@link InventoryApi}; each method gated by {@link NeedsValidation}. */
@RestController
@RequiredArgsConstructor
public class InventoryController implements InventoryApi {

    private final CatalogService catalogService;
    private final DiscoveryService discoveryService;
    private final CurrentUser currentUser;


    @Override
    public List<DatasourceResponse> list() {
        // Collection endpoint: any authenticated caller; results are scoped to grants in the service
        // layer (enforcement layer 2), so no single-resource @NeedsValidation gate here.
        return catalogService.list(currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.DB_VIEW)
    public DatasourceResponse get(@ResourceId UUID id) {
        return catalogService.get(id);
    }

    @Override
    @NeedsValidation(Operation.DISCOVERY_RESCAN)
    public DiscoverySummary rescan() {
        return discoveryService.rescan();
    }

    @Override
    @NeedsValidation(Operation.DB_REGISTER)
    public DatasourceResponse manualAdd(ManualAddRequest request) {
        return catalogService.manualAdd(request, currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.DB_UNREGISTER)
    public void unregister(@ResourceId UUID id) {
        catalogService.unregister(id);
    }
}
