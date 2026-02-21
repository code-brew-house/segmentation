package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FullExecutionResult {
    private String status; // "COMPLETED" or "FAILED"
    private List<NodeResult> nodeResults;
}
