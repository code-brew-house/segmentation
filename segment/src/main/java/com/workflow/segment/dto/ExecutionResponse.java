package com.workflow.segment.dto;
import com.workflow.segment.model.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;
public record ExecutionResponse(UUID id, UUID workflowId, ExecutionStatus status, Instant startedAt, Instant completedAt) {}
