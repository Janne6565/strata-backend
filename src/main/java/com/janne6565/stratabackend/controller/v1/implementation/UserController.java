package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.UserApi;
import com.janne6565.stratabackend.model.action.ChangeRoleRequest;
import com.janne6565.stratabackend.model.action.CreateUserRequest;
import com.janne6565.stratabackend.model.core.UserResponse;
import com.janne6565.stratabackend.security.authorization.AuthorizationAspect;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.security.authorization.ResourceId;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.core.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements {@link UserApi}. Each method is gated by {@link NeedsValidation}; the target user id
 * is marked {@link ResourceId} so the {@link com.janne6565.stratabackend.security.authorization.AuthorizationAspect}
 * can resolve it for instance-level policies.
 */
@RestController
public class UserController implements UserApi {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @Override
    @NeedsValidation(Operation.USER_LIST)
    public List<UserResponse> list() {
        return userService.list();
    }

    @Override
    @NeedsValidation(Operation.USER_CREATE)
    public UserResponse create(CreateUserRequest request) {
        return userService.create(request);
    }

    @Override
    @NeedsValidation(Operation.USER_CHANGE_ROLE)
    public UserResponse changeRole(@ResourceId UUID id, ChangeRoleRequest request) {
        return userService.changeRole(id, request.role());
    }

    @Override
    @NeedsValidation(Operation.USER_DELETE)
    public void delete(@ResourceId UUID id) {
        userService.delete(id, currentUser.require());
    }
}
