package com.janne6565.stratabackend.rbac;

import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.auth.UserRepository;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.NotFoundException;
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

    public ResourceResolver(
            UserRepository userRepository, DatasourceRepository datasourceRepository) {
        this.userRepository = userRepository;
        this.datasourceRepository = datasourceRepository;
    }

    /** Resolves a reference id (UUID or its string form) to a {@link User}, or 404s. */
    public User requireUser(Object referenceId) {
        UUID id = toUuid(referenceId);
        return userRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    /** Resolves a reference id to a {@link Datasource}, or 404s. */
    public Datasource requireDatasource(Object referenceId) {
        UUID id = toUuid(referenceId);
        return datasourceRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Datasource not found: " + id));
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
