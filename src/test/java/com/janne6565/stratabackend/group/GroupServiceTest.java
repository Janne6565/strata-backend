package com.janne6565.stratabackend.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.auth.Role;
import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.NotFoundException;
import com.janne6565.stratabackend.group.dto.CreateGroupRequest;
import com.janne6565.stratabackend.group.dto.GroupResponse;
import com.janne6565.stratabackend.group.dto.UpdateGroupRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the group service: creation, membership and reorder, with mocked repositories. */
class GroupServiceTest {

    private final DbGroupRepository groupRepository = mock(DbGroupRepository.class);
    private final DatasourceRepository datasourceRepository = mock(DatasourceRepository.class);
    private GroupService service;
    private final User owner = new User("dev", "hash", Role.USER);

    @BeforeEach
    void setUp() {
        service = new GroupService(groupRepository, datasourceRepository);
        when(groupRepository.save(any(DbGroup.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private DbGroup group(UUID id, String name, int position) {
        DbGroup group = new DbGroup();
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
        DbGroup group = group(groupId, "Prod", 0);
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
        DbGroup first = group(UUID.randomUUID(), "A", 0);
        DbGroup second = group(UUID.randomUUID(), "B", 1);
        when(groupRepository.findByOwnerUserIdOrderByPositionAsc(owner.getId()))
                .thenReturn(List.of(first, second));

        service.reorder(List.of(second.getId(), first.getId()), owner);

        assertThat(second.getPosition()).isEqualTo(0);
        assertThat(first.getPosition()).isEqualTo(1);
    }
}
