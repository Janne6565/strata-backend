package com.janne6565.stratabackend.model.action;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Add a datasource to a group. */
public record AddMemberRequest(@NotNull UUID datasourceId) {}
