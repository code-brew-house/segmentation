package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FullExecutionInput {
    private String workflowId;
    private String executionId;
    private List<GraphNode> graph;
    private List<GraphEdge> edges;
}
