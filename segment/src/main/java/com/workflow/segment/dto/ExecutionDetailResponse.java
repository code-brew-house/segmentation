package com.workflow.segment.dto;
import com.workflow.segment.model.ExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ExecutionDetailResponse(UUID id, UUID workflowId, ExecutionStatus status,
    Instant startedAt, Instant completedAt, List<NodeExecutionResultResponse> nodeResults) {}
