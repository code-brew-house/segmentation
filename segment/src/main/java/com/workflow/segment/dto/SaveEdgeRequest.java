package com.workflow.segment.dto;

import java.util.Map;

public record SaveEdgeRequest(String id, String name, String source, String target,
                              String sourceHandle, String targetHandle,
                              Map<String, Object> condition, boolean isDefault,
                              Integer sortOrder, Map<String, Object> metadata) {}
