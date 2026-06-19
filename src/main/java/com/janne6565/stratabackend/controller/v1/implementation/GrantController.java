package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.GrantApi;
import com.janne6565.stratabackend.model.action.CreateGrantRequest;
import com.janne6565.stratabackend.model.core.GrantResponse;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.core.GrantService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/** Implements {@link GrantApi}; each method gated by {@link NeedsValidation}. */
@RestController
@RequiredArgsConstructor
public class GrantController implements GrantApi {

    private final GrantService grantService;
    private final CurrentUser currentUser;

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
