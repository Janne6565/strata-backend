package com.janne6565.stratabackend.grant;

import com.janne6565.stratabackend.auth.CurrentUser;
import com.janne6565.stratabackend.grant.dto.CreateGrantRequest;
import com.janne6565.stratabackend.grant.dto.GrantResponse;
import com.janne6565.stratabackend.rbac.NeedsValidation;
import com.janne6565.stratabackend.rbac.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/** Implements {@link GrantApi}; each method gated by {@link NeedsValidation}. */
@RestController
public class GrantController implements GrantApi {

    private final GrantService grantService;
    private final CurrentUser currentUser;

    public GrantController(GrantService grantService, CurrentUser currentUser) {
        this.grantService = grantService;
        this.currentUser = currentUser;
    }

    @Override
    @NeedsValidation(Operation.GRANT_LIST)
    public List<GrantResponse> listForUser(UUID userId) {
        return grantService.listForUser(userId);
    }

    @Override
    @NeedsValidation(Operation.GRANT_CREATE)
    public GrantResponse create(CreateGrantRequest request) {
        return grantService.create(request, currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.GRANT_REVOKE)
    public void revoke(UUID id) {
        grantService.revoke(id);
    }
}
