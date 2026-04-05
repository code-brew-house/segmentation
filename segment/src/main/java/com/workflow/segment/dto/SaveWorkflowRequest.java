package com.workflow.segment.dto;

import java.util.List;
import java.util.Map;

public record SaveWorkflowRequest(List<SaveNodeRequest> nodes, List<SaveEdgeRequest> edges) {
    public record SaveNodeRequest(String id, String type, Map<String, Object> config) {}
}
