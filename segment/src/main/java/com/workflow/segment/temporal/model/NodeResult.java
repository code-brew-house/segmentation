package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeResult {
    private String nodeId;
    private String nodeType;
    private String resultTable;
    private int inputCount;
    private int outputCount;
    private int filteredCount;
    private String status; // "SUCCESS" or "FAILED"
    private String errorMessage;
}
