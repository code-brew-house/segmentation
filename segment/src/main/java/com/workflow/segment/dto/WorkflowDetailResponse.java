package com.workflow.segment.dto;
import com.workflow.segment.model.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record WorkflowDetailResponse(UUID id, String name, String createdBy, Instant createdAt, WorkflowStatus status, List<NodeResponse> nodes) {}
