package com.janne6565.stratabackend.catalog;

import com.janne6565.stratabackend.auth.CurrentUser;
import com.janne6565.stratabackend.catalog.dto.DatasourceResponse;
import com.janne6565.stratabackend.catalog.dto.ManualAddRequest;
import com.janne6565.stratabackend.discovery.DiscoveryService;
import com.janne6565.stratabackend.discovery.DiscoverySummary;
import com.janne6565.stratabackend.rbac.NeedsValidation;
import com.janne6565.stratabackend.rbac.Operation;
import com.janne6565.stratabackend.rbac.ResourceId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/** Implements {@link InventoryApi}; each method gated by {@link NeedsValidation}. */
@RestController
public class InventoryController implements InventoryApi {

    private final CatalogService catalogService;
    private final DiscoveryService discoveryService;
    private final CurrentUser currentUser;

    public InventoryController(
            CatalogService catalogService,
            DiscoveryService discoveryService,
            CurrentUser currentUser) {
        this.catalogService = catalogService;
        this.discoveryService = discoveryService;
        this.currentUser = currentUser;
    }

    @Override
    @NeedsValidation(Operation.DB_VIEW)
    public List<DatasourceResponse> list() {
        return catalogService.list();
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
