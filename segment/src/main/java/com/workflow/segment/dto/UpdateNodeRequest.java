package com.workflow.segment.dto;
import java.util.List;
import java.util.Map;
public record UpdateNodeRequest(List<String> parentNodeIds, Map<String, Object> config) {}
