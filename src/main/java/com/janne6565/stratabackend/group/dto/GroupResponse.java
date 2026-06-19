package com.janne6565.stratabackend.group.dto;

import com.janne6565.stratabackend.group.DbGroup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Public view of a user's datasource group and its membership. */
public record GroupResponse(
        UUID id, String name, int position, List<UUID> datasourceIds, Instant createdAt) {

    public static GroupResponse from(DbGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getPosition(),
                new ArrayList<>(group.getDatasourceIds()),
                group.getCreatedAt());
    }
}
