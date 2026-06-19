package com.janne6565.stratabackend.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename a group. */
public record UpdateGroupRequest(@NotBlank @Size(max = 255) String name) {}
