package com.janne6565.stratabackend.security.authorization;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.DbGroupEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.repository.DbGroupRepository;
import com.janne6565.stratabackend.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Loads the target entity behind a {@code @ResourceId} so policies can make instance-level
 * decisions (ownership, role, flags) rather than coarse role checks. Grows per milestone as new
 * resource types become authorization targets (datasources, grants, groups).
 */
@Component
public class ResourceResolver {

    private final UserRepository userRepository;
    private final DatasourceRepository datasourceRepository;
    private final DbGroupRepository groupRepository;

    public ResourceResolver(
            UserRepository userRepository,
            DatasourceRepository datasourceRepository,
            DbGroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.datasourceRepository = datasourceRepository;
        this.groupRepository = groupRepository;
    }

    /** Resolves a reference id (UUID or its string form) to a {@link UserEntity}, or 404s. */
    public UserEntity requireUser(Object referenceId) {
        UUID id = toUuid(referenceId);
        return userRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("UserEntity not found: " + id));
    }

    /** Resolves a reference id to a {@link DatasourceEntity}, or 404s. */
    public DatasourceEntity requireDatasource(Object referenceId) {
        UUID id = toUuid(referenceId);
        return datasourceRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("DatasourceEntity not found: " + id));
    }

    /** Resolves a reference id to a {@link DbGroupEntity}, or 404s. */
    public DbGroupEntity requireGroup(Object referenceId) {
        UUID id = toUuid(referenceId);
        return groupRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Group not found: " + id));
    }

    private UUID toUuid(Object referenceId) {
        if (referenceId instanceof UUID uuid) {
            return uuid;
        }
        if (referenceId instanceof String s) {
            return UUID.fromString(s);
        }
        throw new IllegalArgumentException(
                "Cannot resolve resource id of type "
                        + (referenceId == null ? "null" : referenceId.getClass().getName()));
    }
}
