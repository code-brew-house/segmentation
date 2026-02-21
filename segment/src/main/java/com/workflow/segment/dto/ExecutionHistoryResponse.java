package com.workflow.segment.dto;

import com.workflow.segment.model.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExecutionHistoryResponse(
    UUID executionId,
    UUID workflowId,
    String workflowName,
    ExecutionStatus status,
    Instant startedAt,
    Instant completedAt,
    int totalNodes,
    int passedNodes,
    int failedNodes
) {}
