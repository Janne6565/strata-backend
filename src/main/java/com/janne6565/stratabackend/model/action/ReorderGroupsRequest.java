package com.janne6565.stratabackend.model.action;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Set the order of the caller's groups; positions follow the given id order. */
public record ReorderGroupsRequest(@NotNull List<UUID> groupIds) {}
