package com.workflow.segment.dto;
import com.workflow.segment.model.NodeType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public record NodeResponse(UUID id, NodeType type, List<UUID> parentNodeIds, Map<String, Object> config, Integer position) {}
