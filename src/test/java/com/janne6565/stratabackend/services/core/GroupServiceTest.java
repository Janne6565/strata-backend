package com.janne6565.stratabackend.services.core;

import com.janne6565.stratabackend.entity.DbGroupEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.CreateGroupRequest;
import com.janne6565.stratabackend.model.action.UpdateGroupRequest;
import com.janne6565.stratabackend.model.core.GroupResponse;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.repository.DbGroupRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for the group service: creation, membership and reorder, with mocked repositories. */
class GroupServiceTest {

    private final DbGroupRepository groupRepository = mock(DbGroupRepository.class);
    private final DatasourceRepository datasourceRepository = mock(DatasourceRepository.class);
    private GroupService service;
    private final UserEntity owner = new UserEntity("dev", "hash", Role.USER);

    @BeforeEach
    void setUp() {
        service = new GroupService(groupRepository, datasourceRepository);
        when(groupRepository.save(any(DbGroupEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private DbGroupEntity group(UUID id, String name, int position) {
        DbGroupEntity group = new DbGroupEntity();
        group.setId(id);
        group.setOwnerUserId(owner.getId());
        group.setName(name);
        group.setPosition(position);
        group.setCreatedAt(Instant.now());
        return group;
    }

    @Test
    void createAppendsAtNextPosition() {
        when(groupRepository.countByOwnerUserId(owner.getId())).thenReturn(2L);

        GroupResponse response = service.create(new CreateGroupRequest("Prod"), owner);

        assertThat(response.name()).isEqualTo("Prod");
        assertThat(response.position()).isEqualTo(2);
        assertThat(response.datasourceIds()).isEmpty();
    }

    @Test
    void addMemberRequiresAnExistingDatasource() {
        UUID groupId = UUID.randomUUID();
        UUID datasourceId = UUID.randomUUID();
        when(datasourceRepository.existsById(datasourceId)).thenReturn(false);

        assertThatThrownBy(() -> service.addMember(groupId, datasourceId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addAndRemoveMemberMutatesMembership() {
        UUID groupId = UUID.randomUUID();
        UUID datasourceId = UUID.randomUUID();
        DbGroupEntity group = group(groupId, "Prod", 0);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(datasourceRepository.existsById(datasourceId)).thenReturn(true);

        GroupResponse added = service.addMember(groupId, datasourceId);
        assertThat(added.datasourceIds()).containsExactly(datasourceId);

        GroupResponse removed = service.removeMember(groupId, datasourceId);
        assertThat(removed.datasourceIds()).isEmpty();
    }

    @Test
    void renameChangesTheName() {
        UUID groupId = UUID.randomUUID();
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group(groupId, "Old", 0)));

        GroupResponse response = service.rename(groupId, new UpdateGroupRequest("New"));

        assertThat(response.name()).isEqualTo("New");
    }

    @Test
    void reorderAssignsPositionsByGivenOrder() {
        DbGroupEntity first = group(UUID.randomUUID(), "A", 0);
        DbGroupEntity second = group(UUID.randomUUID(), "B", 1);
        when(groupRepository.findByOwnerUserIdOrderByPositionAsc(owner.getId()))
                .thenReturn(List.of(first, second));

        service.reorder(List.of(second.getId(), first.getId()), owner);

        assertThat(second.getPosition()).isEqualTo(0);
        assertThat(first.getPosition()).isEqualTo(1);
    }
}
