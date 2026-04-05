package com.workflow.segment.temporal.workflows;

import com.workflow.segment.temporal.activities.SegmentActivities;
import com.workflow.segment.temporal.model.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class FullExecutionWorkflowImpl implements FullExecutionWorkflow {

    private final SegmentActivities activities = Workflow.newActivityStub(
            SegmentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    @Override
    public FullExecutionResult execute(FullExecutionInput input) {
        List<GraphNode> graph = input.getGraph();
        List<GraphEdge> edges = input.getEdges() != null ? input.getEdges() : List.of();

        String wfId = input.getWorkflowId().replace("-", "").substring(0, 8);
        String execId = input.getExecutionId().replace("-", "").substring(0, 8);

        // Build lookup maps
        Map<String, GraphNode> nodeMap = graph.stream()
                .collect(Collectors.toMap(GraphNode::getNodeId, n -> n));

        // Build edge maps
        Map<String, List<GraphEdge>> outgoingEdges = new HashMap<>();
        Map<String, List<GraphEdge>> incomingEdges = new HashMap<>();
        for (GraphEdge edge : edges) {
            outgoingEdges.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTargetNodeId(), k -> new ArrayList<>()).add(edge);
        }
        // Sort outgoing edges by sortOrder for deterministic SPLIT evaluation
        outgoingEdges.values().forEach(list -> list.sort(Comparator.comparingInt(GraphEdge::getSortOrder)));

        // Track results and promises
        Map<String, Promise<NodeResult>> promiseMap = new HashMap<>();
        List<NodeResult> allResults = Collections.synchronizedList(new ArrayList<>());

        // Find root nodes (no incoming edges)
        List<GraphNode> roots = graph.stream()
                .filter(n -> !incomingEdges.containsKey(n.getNodeId()))
                .toList();

        // Execute each root and its subtree
        for (GraphNode root : roots) {
            executeNode(root, nodeMap, outgoingEdges, incomingEdges, promiseMap, allResults, wfId, execId);
        }

        // Wait for all promises to complete
        List<Promise<NodeResult>> allPromises = new ArrayList<>(promiseMap.values());
        if (!allPromises.isEmpty()) {
            Promise.allOf(allPromises).get();
        }

        boolean anyFailed = allResults.stream().anyMatch(r -> "FAILED".equals(r.getStatus()));
        String finalStatus = anyFailed ? "FAILED" : "SUCCESS";

        activities.completeExecution(input.getExecutionId(), allResults, finalStatus);

        return new FullExecutionResult(finalStatus, allResults);
    }

    private void executeNode(GraphNode node, Map<String, GraphNode> nodeMap,
                             Map<String, List<GraphEdge>> outgoingEdges,
                             Map<String, List<GraphEdge>> incomingEdges,
                             Map<String, Promise<NodeResult>> promiseMap,
                             List<NodeResult> allResults,
                             String wfId, String execId) {
        if (promiseMap.containsKey(node.getNodeId())) return;

        String nodeType = node.getType();

        if ("SPLIT".equals(nodeType) || "JOIN".equals(nodeType)) {
            Promise<NodeResult> structuralPromise;

            if ("JOIN".equals(nodeType)) {
                List<GraphEdge> incoming = incomingEdges.getOrDefault(node.getNodeId(), List.of());
                List<Promise<NodeResult>> parentPromises = incoming.stream()
                        .map(edge -> {
                            if (!promiseMap.containsKey(edge.getSourceNodeId())) {
                                executeNode(nodeMap.get(edge.getSourceNodeId()), nodeMap,
                                        outgoingEdges, incomingEdges, promiseMap, allResults, wfId, execId);
                            }
                            return promiseMap.get(edge.getSourceNodeId());
                        })
                        .toList();

                Promise<NodeResult> firstParentPromise = parentPromises.get(0);
                structuralPromise = Promise.allOf(parentPromises).thenApply(v -> {
                    String resultTable = firstParentPromise.get().getResultTable();
                    NodeResult nr = new NodeResult(node.getNodeId(), nodeType, resultTable,
                            0, 0, 0, "SUCCESS", null, null);
                    allResults.add(nr);
                    return nr;
                });
            } else {
                // SPLIT: passthrough — condition evaluation happens when scheduling children
                List<GraphEdge> incoming = incomingEdges.getOrDefault(node.getNodeId(), List.of());
                if (!incoming.isEmpty()) {
                    String parentId = incoming.get(0).getSourceNodeId();
                    if (!promiseMap.containsKey(parentId)) {
                        executeNode(nodeMap.get(parentId), nodeMap, outgoingEdges, incomingEdges,
                                promiseMap, allResults, wfId, execId);
                    }
                    structuralPromise = promiseMap.get(parentId).thenApply(parentResult -> {
                        NodeResult nr = new NodeResult(node.getNodeId(), nodeType,
                                parentResult.getResultTable(), 0, 0, 0, "SUCCESS", null, null);
                        allResults.add(nr);
                        return nr;
                    });
                } else {
                    structuralPromise = Async.function(() -> {
                        NodeResult nr = new NodeResult(node.getNodeId(), nodeType, null,
                                0, 0, 0, "SUCCESS", null, null);
                        allResults.add(nr);
                        return nr;
                    });
                }
            }

            promiseMap.put(node.getNodeId(), structuralPromise);

            // Schedule children via outgoing edges
            List<GraphEdge> outgoing = outgoingEdges.getOrDefault(node.getNodeId(), List.of());
            for (GraphEdge edge : outgoing) {
                GraphNode child = nodeMap.get(edge.getTargetNodeId());
                if (child != null) {
                    executeNode(child, nodeMap, outgoingEdges, incomingEdges,
                            promiseMap, allResults, wfId, execId);
                }
            }
            return;
        }

        // Data nodes: wait for parent via incoming edge, then execute
        Promise<NodeResult> nodePromise;
        List<GraphEdge> incoming = incomingEdges.getOrDefault(node.getNodeId(), List.of());

        if (!incoming.isEmpty()) {
            String parentId = incoming.get(0).getSourceNodeId();
            if (!promiseMap.containsKey(parentId)) {
                executeNode(nodeMap.get(parentId), nodeMap, outgoingEdges, incomingEdges,
                        promiseMap, allResults, wfId, execId);
            }
            nodePromise = promiseMap.get(parentId).thenApply(parentResult ->
                    executeDataNode(node, parentResult.getResultTable(), wfId, execId, allResults));
        } else {
            nodePromise = Async.function(() -> executeDataNode(node, null, wfId, execId, allResults));
        }

        promiseMap.put(node.getNodeId(), nodePromise);

        // Schedule children via outgoing edges
        List<GraphEdge> outgoing = outgoingEdges.getOrDefault(node.getNodeId(), List.of());
        for (GraphEdge edge : outgoing) {
            GraphNode child = nodeMap.get(edge.getTargetNodeId());
            if (child != null) {
                executeNode(child, nodeMap, outgoingEdges, incomingEdges,
                        promiseMap, allResults, wfId, execId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private NodeResult executeDataNode(GraphNode node, String sourceTable,
                                       String wfId, String execId, List<NodeResult> allResults) {
        String nodeShort = node.getNodeId().replace("-", "").substring(0, 8);
        String targetTable = "wf_" + wfId + "_e_" + execId + "_n_" + nodeShort + "_r";
        NodeResult result;

        try {
            result = switch (node.getType()) {
                case "START_FILE_UPLOAD" -> {
                    String filePath = (String) node.getConfig().get("file_path");
                    var r = activities.fileUploadActivity(new FileUploadInput(filePath, targetTable, null));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            0, r.getRowCount(), 0, "SUCCESS", null, null);
                }
                case "START_QUERY" -> {
                    String rawSql = (String) node.getConfig().get("raw_sql");
                    var r = activities.startQueryActivity(new StartQueryInput(rawSql, targetTable));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            0, r.getRowCount(), 0, "SUCCESS", null, null);
                }
                case "FILTER" -> {
                    var config = node.getConfig();
                    var r = activities.filterActivity(new FilterInput(
                            sourceTable, targetTable,
                            (String) config.get("data_mart_table"), (String) config.get("join_key"),
                            (String) config.get("mode"), (Map<String, Object>) config.get("conditions"),
                            !Boolean.FALSE.equals(config.get("distinct"))));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            r.getInputCount(), r.getOutputCount(), r.getFilteredCount(), "SUCCESS", null, null);
                }
                case "ENRICH" -> {
                    var config = node.getConfig();
                    var r = activities.enrichActivity(new EnrichInput(
                            sourceTable, targetTable,
                            (String) config.get("data_mart_table"), (String) config.get("mode"),
                            (String) config.get("join_key"),
                            config.get("select_columns") != null ? (List<String>) config.get("select_columns") : null));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            r.getInputCount(), r.getOutputCount(), 0, "SUCCESS", null, null);
                }
                case "STOP" -> {
                    Object rawPath = node.getConfig() != null ? node.getConfig().get("output_file_path") : null;
                    String outputPath = (rawPath instanceof String s && !s.isBlank()) ? s
                            : "./outputs/wf_" + wfId + "/exec_" + execId + "/stop_" + nodeShort + ".csv";
                    var r = activities.stopNodeActivity(new StopNodeInput(sourceTable, outputPath));
                    yield new NodeResult(node.getNodeId(), node.getType(), null,
                            r.getRowCount(), r.getRowCount(), 0, "SUCCESS", null, r.getFilePath());
                }
                default -> throw new RuntimeException("Unknown node type: " + node.getType());
            };
        } catch (Exception e) {
            result = new NodeResult(node.getNodeId(), node.getType(), null,
                    0, 0, 0, "FAILED", e.getMessage(), null);
        }

        allResults.add(result);
        return result;
    }
}
