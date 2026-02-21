package com.workflow.segment.dto;

import java.util.List;
import java.util.UUID;

public record DataMartDetailResponse(UUID id, String tableName, String schemaName, String description, List<DataMartColumnResponse> columns) {}
