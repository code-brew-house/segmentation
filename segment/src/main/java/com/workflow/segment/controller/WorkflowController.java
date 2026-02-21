package com.workflow.segment.controller;

import com.workflow.segment.dto.*;
import com.workflow.segment.service.ExecutionService;
import com.workflow.segment.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService workflowService;
    private final ExecutionService executionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse create(@RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(request);
    }

    @GetMapping
    public List<WorkflowResponse> list() { return workflowService.listWorkflows(); }

    @GetMapping("/{id}")
    public WorkflowDetailResponse get(@PathVariable UUID id) { return workflowService.getWorkflow(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { workflowService.deleteWorkflow(id); }

    @GetMapping("/{id}/executions")
    public List<ExecutionResponse> listExecutions(@PathVariable UUID id) {
        return executionService.listExecutions(id);
    }

    @GetMapping("/{id}/executions/{execId}")
    public ExecutionDetailResponse getExecution(@PathVariable UUID id, @PathVariable UUID execId) {
        return executionService.getExecution(execId);
    }
}
