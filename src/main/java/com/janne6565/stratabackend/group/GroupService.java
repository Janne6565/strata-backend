package com.janne6565.stratabackend.group;

import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.NotFoundException;
import com.janne6565.stratabackend.group.dto.CreateGroupRequest;
import com.janne6565.stratabackend.group.dto.GroupResponse;
import com.janne6565.stratabackend.group.dto.UpdateGroupRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a user's datasource groups (ARCHITECTURE.md §4). Instance-level ownership is enforced
 * upstream by the policy aspect ({@code GROUP_*} operations); this service additionally scopes
 * collection operations (list, reorder) to the caller as defence-in-depth.
 */
@Service
public class GroupService {

    private final DbGroupRepository groupRepository;
    private final DatasourceRepository datasourceRepository;

    public GroupService(
            DbGroupRepository groupRepository, DatasourceRepository datasourceRepository) {
        this.groupRepository = groupRepository;
        this.datasourceRepository = datasourceRepository;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listForOwner(User owner) {
        return groupRepository.findByOwnerUserIdOrderByPositionAsc(owner.getId()).stream()
                .map(GroupResponse::from)
                .toList();
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest request, User owner) {
        DbGroup group = new DbGroup();
        group.setId(UUID.randomUUID());
        group.setOwnerUserId(owner.getId());
        group.setName(request.name());
        group.setPosition((int) groupRepository.countByOwnerUserId(owner.getId()));
        group.setCreatedAt(Instant.now());
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse rename(UUID groupId, UpdateGroupRequest request) {
        DbGroup group = require(groupId);
        group.setName(request.name());
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public void reorder(List<UUID> orderedIds, User owner) {
        List<DbGroup> owned = groupRepository.findByOwnerUserIdOrderByPositionAsc(owner.getId());
        Map<UUID, DbGroup> byId =
                owned.stream().collect(Collectors.toMap(DbGroup::getId, Function.identity()));
        int position = 0;
        for (UUID id : orderedIds) {
            DbGroup group = byId.get(id);
            if (group != null) {
                group.setPosition(position++);
            }
        }
        groupRepository.saveAll(owned);
    }

    @Transactional
    public void delete(UUID groupId) {
        groupRepository.deleteById(groupId);
    }

    @Transactional
    public GroupResponse addMember(UUID groupId, UUID datasourceId) {
        if (!datasourceRepository.existsById(datasourceId)) {
            throw new NotFoundException("Datasource not found: " + datasourceId);
        }
        DbGroup group = require(groupId);
        group.getDatasourceIds().add(datasourceId);
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse removeMember(UUID groupId, UUID datasourceId) {
        DbGroup group = require(groupId);
        group.getDatasourceIds().remove(datasourceId);
        return GroupResponse.from(groupRepository.save(group));
    }

    private DbGroup require(UUID groupId) {
        return groupRepository
                .findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found: " + groupId));
    }
}
