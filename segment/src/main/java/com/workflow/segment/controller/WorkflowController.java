package com.workflow.segment.controller;

import com.workflow.segment.dto.*;
import com.workflow.segment.service.ExecutionService;
import com.workflow.segment.service.SqlPreviewService;
import com.workflow.segment.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService workflowService;
    private final ExecutionService executionService;
    private final SqlPreviewService sqlPreviewService;

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

    @PutMapping("/{id}")
    public WorkflowDetailResponse save(@PathVariable UUID id, @RequestBody SaveWorkflowRequest request) {
        return workflowService.saveWorkflow(id, request);
    }

    @GetMapping("/{id}/executions")
    public List<ExecutionResponse> listExecutions(@PathVariable UUID id) {
        return executionService.listExecutions(id);
    }

    @GetMapping("/{id}/executions/{execId}")
    public ExecutionDetailResponse getExecution(@PathVariable UUID id, @PathVariable UUID execId) {
        return executionService.getExecution(execId);
    }

    @PostMapping("/{id}/execute")
    public ExecutionResponse executeWorkflow(@PathVariable UUID id) {
        return executionService.executeWorkflow(id);
    }

    @GetMapping("/{id}/executions/{execId}/nodes/{nodeId}/results")
    public List<Map<String, Object>> getNodeResults(@PathVariable UUID id,
                                                     @PathVariable UUID execId,
                                                     @PathVariable UUID nodeId) {
        return executionService.getNodeResults(execId, nodeId);
    }

    @GetMapping("/{id}/executions/{execId}/nodes/{nodeId}/download")
    public ResponseEntity<Resource> downloadCsv(@PathVariable UUID id,
                                                 @PathVariable UUID execId,
                                                 @PathVariable UUID nodeId) {
        return executionService.downloadCsv(execId, nodeId);
    }

    @PostMapping("/{id}/nodes/{nodeId}/preview")
    public Map<String, Object> previewNode(@PathVariable UUID id, @PathVariable UUID nodeId) {
        return executionService.previewNode(id, nodeId);
    }

    @PostMapping("/{id}/nodes/{nodeId}/sql-preview")
    public SqlPreviewResponse sqlPreview(@PathVariable UUID id, @PathVariable UUID nodeId,
                                          @RequestBody SqlPreviewRequest request) {
        return sqlPreviewService.generatePreview(request.nodeType(), request.config());
    }
}
