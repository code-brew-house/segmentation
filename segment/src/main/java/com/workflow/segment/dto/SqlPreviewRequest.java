package com.workflow.segment.dto;

import java.util.Map;

public record SqlPreviewRequest(String nodeType, Map<String, Object> config) {}
