package com.workflow.segment.dto;

import java.util.UUID;

public record DataMartResponse(UUID id, String tableName, String schemaName, String description, int columnCount) {}
