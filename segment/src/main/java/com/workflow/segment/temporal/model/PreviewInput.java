package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreviewInput {
    private String workflowId;
    private String executionId;
    private String nodeId;
    private String nodeType; // "START_FILE_UPLOAD", "START_QUERY", "FILTER", "ENRICH", "STOP"
    private Map<String, Object> config;
    private String sourceTable; // null for start nodes
}
