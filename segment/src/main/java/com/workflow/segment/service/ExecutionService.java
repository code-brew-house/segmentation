package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.*;
import com.workflow.segment.repository.NodeExecutionResultRepository;
import com.workflow.segment.repository.SegmentWorkflowEdgeRepository;
import com.workflow.segment.repository.SegmentWorkflowNodeRepository;
import com.workflow.segment.repository.SegmentWorkflowRepository;
import com.workflow.segment.repository.WorkflowExecutionRepository;
import com.workflow.segment.temporal.config.TemporalConfig;
import com.workflow.segment.temporal.model.*;
import com.workflow.segment.temporal.workflows.FullExecutionWorkflow;
import com.workflow.segment.temporal.workflows.PreviewWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.util.*;

@Service
public class ExecutionService {
    private final WorkflowExecutionRepository executionRepository;
    private final SegmentWorkflowRepository workflowRepository;
    private final SegmentWorkflowNodeRepository nodeRepository;
    private final NodeExecutionResultRepository nodeExecutionResultRepository;
    private final SegmentWorkflowEdgeRepository edgeRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private WorkflowClient workflowClient;

    public ExecutionService(WorkflowExecutionRepository executionRepository,
                            SegmentWorkflowRepository workflowRepository,
                            SegmentWorkflowNodeRepository nodeRepository,
                            NodeExecutionResultRepository nodeExecutionResultRepository,
                            SegmentWorkflowEdgeRepository edgeRepository,
                            JdbcTemplate jdbcTemplate) {
        this.executionRepository = executionRepository;
        this.workflowRepository = workflowRepository;
        this.nodeRepository = nodeRepository;
        this.nodeExecutionResultRepository = nodeExecutionResultRepository;
        this.edgeRepository = edgeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExecutionResponse> listExecutions(UUID workflowId) {
        return executionRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId).stream()
                .map(e -> new ExecutionResponse(e.getId(), e.getWorkflow().getId(), e.getStatus(),
                        e.getStartedAt(), e.getCompletedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExecutionHistoryResponse> listAllExecutions() {
        return executionRepository.findAllByOrderByStartedAtDesc().stream()
                .map(e -> {
                    List<NodeExecutionResult> results = e.getNodeResults();
                    int total = results.size();
                    int passed = (int) results.stream().filter(r -> r.getStatus() == ExecutionStatus.SUCCESS).count();
                    int failed = (int) results.stream().filter(r -> r.getStatus() == ExecutionStatus.FAILED).count();
                    return new ExecutionHistoryResponse(
                            e.getId(), e.getWorkflow().getId(), e.getWorkflow().getName(),
                            e.getStatus(), e.getStartedAt(), e.getCompletedAt(),
                            total, passed, failed);
                }).toList();
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

    @Transactional
    public ExecutionResponse executeWorkflow(UUID workflowId) {
        SegmentWorkflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        // Create execution instance
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflow(wf);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedAt(Instant.now());
        execution = executionRepository.save(execution);

        // Build graph input
        List<SegmentWorkflowNode> nodes = nodeRepository.findByWorkflowId(workflowId);
        List<GraphNode> graphNodes = nodes.stream().map(n -> new GraphNode(
                n.getId().toString(), n.getType().name(), n.getConfig()
        )).toList();

        List<SegmentWorkflowEdge> edges = edgeRepository.findByWorkflowId(workflowId);
        List<GraphEdge> graphEdges = edges.stream().map(e -> new GraphEdge(
                e.getId().toString(),
                e.getSourceNode().getId().toString(),
                e.getTargetNode().getId().toString(),
                e.getName(),
                e.getCondition(),
                e.isDefault(),
                e.getSortOrder() != null ? e.getSortOrder() : 0
        )).toList();

        FullExecutionInput input = new FullExecutionInput(
                workflowId.toString(), execution.getId().toString(), graphNodes, graphEdges);

        // Start Temporal workflow async (null in test profile)
        if (workflowClient != null) {
            var wfStub = workflowClient.newWorkflowStub(FullExecutionWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TemporalConfig.TASK_QUEUE)
                            .setWorkflowId("exec-" + execution.getId())
                            .build());
            WorkflowClient.start(wfStub::execute, input);
        }

        return new ExecutionResponse(execution.getId(), workflowId, execution.getStatus(),
                execution.getStartedAt(), execution.getCompletedAt());
    }

    public List<Map<String, Object>> getNodeResults(UUID execId, UUID nodeId) {
        NodeExecutionResult ner = nodeExecutionResultRepository
                .findByExecutionIdAndNodeId(execId, nodeId)
                .orElseThrow(() -> new RuntimeException(
                        "Node execution result not found for execution " + execId + " node " + nodeId));

        String tableName = ner.getResultTableName();
        if (tableName == null || tableName.isBlank()) {
            return List.of();
        }

        try {
            return jdbcTemplate.queryForList("SELECT * FROM " + tableName + " LIMIT 100");
        } catch (Exception e) {
            return List.of();
        }
    }

    public ResponseEntity<Resource> downloadCsv(UUID execId, UUID nodeId) {
        NodeExecutionResult ner = nodeExecutionResultRepository
                .findByExecutionIdAndNodeId(execId, nodeId)
                .orElseThrow(() -> new RuntimeException(
                        "Node execution result not found for execution " + execId + " node " + nodeId));

        String outputPath = ner.getOutputFilePath();
        if (outputPath == null || outputPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(outputPath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    @Transactional
    public Map<String, Object> previewNode(UUID workflowId, UUID nodeId) {
        SegmentWorkflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        SegmentWorkflowNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        // Create a temporary execution record
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflow(wf);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedAt(Instant.now());
        execution = executionRepository.save(execution);

        // Resolve source table from parent node's previous execution
        String sourceTable = null;
        List<SegmentWorkflowEdge> incomingEdges = edgeRepository
                .findBySourceNodeIdOrTargetNodeId(nodeId, nodeId).stream()
                .filter(e -> e.getTargetNode().getId().equals(nodeId))
                .toList();
        if (!incomingEdges.isEmpty()) {
            UUID parentNodeId = incomingEdges.get(0).getSourceNode().getId();
            Optional<NodeExecutionResult> parentResult = nodeExecutionResultRepository
                    .findTopByNodeIdAndStatusOrderByStartedAtDesc(parentNodeId, ExecutionStatus.SUCCESS);
            if (parentResult.isPresent()) {
                sourceTable = parentResult.get().getResultTableName();
            }
        }

        PreviewInput previewInput = new PreviewInput(
                workflowId.toString(), execution.getId().toString(),
                nodeId.toString(), node.getType().name(),
                node.getConfig(), sourceTable);

        // Start Temporal preview workflow (null in test profile)
        if (workflowClient != null) {
            var wfStub = workflowClient.newWorkflowStub(PreviewWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TemporalConfig.TASK_QUEUE)
                            .setWorkflowId("preview-" + execution.getId())
                            .build());
            WorkflowClient.start(wfStub::execute, previewInput);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", execution.getId());
        response.put("workflowId", workflowId);
        response.put("nodeId", nodeId);
        response.put("status", execution.getStatus());
        return response;
    }
}
