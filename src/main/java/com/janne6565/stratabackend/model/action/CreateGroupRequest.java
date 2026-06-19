package com.janne6565.stratabackend.model.action;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create a new datasource group owned by the caller. */
public record CreateGroupRequest(@NotBlank @Size(max = 255) String name) {}
