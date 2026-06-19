package com.janne6565.stratabackend.controller.v1.implementation;

import com.janne6565.stratabackend.controller.v1.schema.GroupApi;
import com.janne6565.stratabackend.model.action.AddMemberRequest;
import com.janne6565.stratabackend.model.action.CreateGroupRequest;
import com.janne6565.stratabackend.model.action.ReorderGroupsRequest;
import com.janne6565.stratabackend.model.action.UpdateGroupRequest;
import com.janne6565.stratabackend.model.core.GroupResponse;
import com.janne6565.stratabackend.security.authorization.NeedsValidation;
import com.janne6565.stratabackend.security.authorization.Operation;
import com.janne6565.stratabackend.security.authorization.ResourceId;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.core.GroupService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements {@link GroupApi}. Instance operations carry a {@link ResourceId} so the policy aspect
 * can enforce that the caller owns the group; collection operations (list, create, reorder) are
 * scoped to the caller in the service.
 */
@RestController
public class GroupController implements GroupApi {

    private final GroupService groupService;
    private final CurrentUser currentUser;

    public GroupController(GroupService groupService, CurrentUser currentUser) {
        this.groupService = groupService;
        this.currentUser = currentUser;
    }

    @Override
    @NeedsValidation(Operation.GROUP_LIST)
    public List<GroupResponse> list() {
        return groupService.listForOwner(currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.GROUP_CREATE)
    public GroupResponse create(CreateGroupRequest request) {
        return groupService.create(request, currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.GROUP_UPDATE)
    public GroupResponse rename(@ResourceId UUID id, UpdateGroupRequest request) {
        return groupService.rename(id, request);
    }

    @Override
    @NeedsValidation(Operation.GROUP_UPDATE)
    public void reorder(ReorderGroupsRequest request) {
        groupService.reorder(request.groupIds(), currentUser.require());
    }

    @Override
    @NeedsValidation(Operation.GROUP_DELETE)
    public void delete(@ResourceId UUID id) {
        groupService.delete(id);
    }

    @Override
    @NeedsValidation(Operation.GROUP_UPDATE)
    public GroupResponse addMember(@ResourceId UUID id, AddMemberRequest request) {
        return groupService.addMember(id, request.datasourceId());
    }

    @Override
    @NeedsValidation(Operation.GROUP_UPDATE)
    public GroupResponse removeMember(@ResourceId UUID id, UUID datasourceId) {
        return groupService.removeMember(id, datasourceId);
    }
}
