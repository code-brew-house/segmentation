package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphEdge {
    private String edgeId;
    private String sourceNodeId;
    private String targetNodeId;
    private String name;
    private Map<String, Object> condition;
    private boolean isDefault;
    private int sortOrder;
}
