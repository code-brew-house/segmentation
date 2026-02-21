package com.workflow.segment.dto;
import com.workflow.segment.model.ExecutionStatus;
import com.workflow.segment.model.NodeType;
import java.util.UUID;
public record NodeExecutionResultResponse(UUID nodeId, NodeType nodeType, ExecutionStatus status,
    Integer inputRecordCount, Integer filteredRecordCount, Integer outputRecordCount,
    String resultTableName, String outputFilePath, String errorMessage) {}
