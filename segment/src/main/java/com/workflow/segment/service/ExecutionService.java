package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.WorkflowExecution;
import com.workflow.segment.repository.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionService {
    private final WorkflowExecutionRepository executionRepository;

    public List<ExecutionResponse> listExecutions(UUID workflowId) {
        return executionRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId).stream()
                .map(e -> new ExecutionResponse(e.getId(), e.getWorkflow().getId(), e.getStatus(), e.getStartedAt(), e.getCompletedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExecutionDetailResponse getExecution(UUID execId) {
        WorkflowExecution exec = executionRepository.findById(execId)
                .orElseThrow(() -> new RuntimeException("Execution not found: " + execId));
        return new ExecutionDetailResponse(exec.getId(), exec.getWorkflow().getId(), exec.getStatus(),
                exec.getStartedAt(), exec.getCompletedAt(),
                exec.getNodeResults().stream().map(nr -> new NodeExecutionResultResponse(
                        nr.getNodeId(), nr.getNodeType(), nr.getStatus(),
                        nr.getInputRecordCount(), nr.getFilteredRecordCount(), nr.getOutputRecordCount(),
                        nr.getResultTableName(), nr.getOutputFilePath(), nr.getErrorMessage()
                )).toList());
    }
}
