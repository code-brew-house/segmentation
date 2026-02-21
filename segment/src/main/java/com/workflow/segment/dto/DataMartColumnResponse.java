package com.workflow.segment.dto;

import java.util.UUID;

public record DataMartColumnResponse(UUID id, String columnName, String dataType, String description, Integer ordinalPosition) {}
