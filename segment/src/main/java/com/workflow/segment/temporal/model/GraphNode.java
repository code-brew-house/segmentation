package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphNode {
    private String nodeId;
    private String type;
    private List<String> parentIds;
    private Map<String, Object> config;
}
