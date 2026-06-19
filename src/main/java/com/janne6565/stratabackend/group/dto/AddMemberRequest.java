package com.janne6565.stratabackend.group.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Add a datasource to a group. */
public record AddMemberRequest(@NotNull UUID datasourceId) {}
