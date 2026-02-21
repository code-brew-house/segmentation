package com.workflow.segment.dto;

import java.util.List;
import java.util.Map;

public record SaveWorkflowRequest(List<SaveNodeRequest> nodes) {
    public record SaveNodeRequest(String id, String type, List<String> parentNodeIds,
                                   Map<String, Object> config, Integer position) {}
}
