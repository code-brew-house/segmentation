package com.workflow.segment.dto;
import com.workflow.segment.model.WorkflowStatus;
import java.time.Instant;
import java.util.UUID;
public record WorkflowResponse(UUID id, String name, String createdBy, Instant createdAt, WorkflowStatus status, int nodeCount) {}
