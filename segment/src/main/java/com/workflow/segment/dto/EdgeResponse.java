package com.workflow.segment.dto;

import java.util.Map;
import java.util.UUID;

public record EdgeResponse(UUID id, String name, UUID sourceNodeId, UUID targetNodeId,
                           String sourceHandle, String targetHandle,
                           Map<String, Object> condition, boolean isDefault,
                           Integer sortOrder, Map<String, Object> metadata) {}
