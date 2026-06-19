package com.janne6565.stratabackend.services.core;

import com.janne6565.stratabackend.entity.DbGroupEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.CreateGroupRequest;
import com.janne6565.stratabackend.model.action.UpdateGroupRequest;
import com.janne6565.stratabackend.model.core.GroupResponse;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.repository.DbGroupRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a user's datasource groups (ARCHITECTURE.md §4). Instance-level ownership is enforced
 * upstream by the policy aspect ({@code GROUP_*} operations); this service additionally scopes
 * collection operations (list, reorder) to the caller as defence-in-depth.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final DbGroupRepository groupRepository;
    private final DatasourceRepository datasourceRepository;

    @Transactional(readOnly = true)
    public List<GroupResponse> listForOwner(UserEntity owner) {
        return groupRepository.findByOwnerUserIdOrderByPositionAsc(owner.getId()).stream()
                .map(GroupResponse::from)
                .toList();
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest request, UserEntity owner) {
        DbGroupEntity group = new DbGroupEntity();
        group.setId(UUID.randomUUID());
        group.setOwnerUserId(owner.getId());
        group.setName(request.name());
        group.setPosition((int) groupRepository.countByOwnerUserId(owner.getId()));
        group.setCreatedAt(Instant.now());
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse rename(UUID groupId, UpdateGroupRequest request) {
        DbGroupEntity group = require(groupId);
        group.setName(request.name());
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public void reorder(List<UUID> orderedIds, UserEntity owner) {
        List<DbGroupEntity> owned =
                groupRepository.findByOwnerUserIdOrderByPositionAsc(owner.getId());
        Map<UUID, DbGroupEntity> byId =
                owned.stream().collect(Collectors.toMap(DbGroupEntity::getId, Function.identity()));
        int position = 0;
        for (UUID id : orderedIds) {
            DbGroupEntity group = byId.get(id);
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
            throw new NotFoundException("DatasourceEntity not found: " + datasourceId);
        }
        DbGroupEntity group = require(groupId);
        group.getDatasourceIds().add(datasourceId);
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse removeMember(UUID groupId, UUID datasourceId) {
        DbGroupEntity group = require(groupId);
        group.getDatasourceIds().remove(datasourceId);
        return GroupResponse.from(groupRepository.save(group));
    }

    private DbGroupEntity require(UUID groupId) {
        return groupRepository
                .findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found: " + groupId));
    }
}
