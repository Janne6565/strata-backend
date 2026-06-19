package com.janne6565.stratabackend.model.core;

import java.util.List;

/** The introspected structure of a database: its user tables and views. */
public record SchemaInfo(List<TableInfo> tables) {}
