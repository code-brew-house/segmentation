package com.workflow.segment.dto;
import java.util.List;
import java.util.Map;
public record AddNodeRequest(List<String> parentNodeIds, String type, Map<String, Object> config, Integer position) {}
