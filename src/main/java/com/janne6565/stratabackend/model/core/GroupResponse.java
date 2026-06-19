package com.janne6565.stratabackend.model.core;

import com.janne6565.stratabackend.entity.DbGroupEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Public view of a user's datasource group and its membership. */
public record GroupResponse(
        UUID id, String name, int position, List<UUID> datasourceIds, Instant createdAt) {

    public static GroupResponse from(DbGroupEntity group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getPosition(),
                new ArrayList<>(group.getDatasourceIds()),
                group.getCreatedAt());
    }
}
