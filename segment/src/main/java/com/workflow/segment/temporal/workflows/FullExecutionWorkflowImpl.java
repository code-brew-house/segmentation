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
        String wfId = input.getWorkflowId();
        String execId = input.getExecutionId();

        // Build lookup maps
        Map<String, GraphNode> nodeMap = graph.stream()
                .collect(Collectors.toMap(GraphNode::getNodeId, n -> n));
        Map<String, List<String>> childrenMap = new HashMap<>();
        for (GraphNode node : graph) {
            if (node.getParentIds() != null) {
                for (String parentId : node.getParentIds()) {
                    childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node.getNodeId());
                }
            }
        }

        // Track results and promises
        Map<String, Promise<NodeResult>> promiseMap = new HashMap<>();
        List<NodeResult> allResults = Collections.synchronizedList(new ArrayList<>());

        // Find root nodes (no parents)
        List<GraphNode> roots = graph.stream()
                .filter(n -> n.getParentIds() == null || n.getParentIds().isEmpty())
                .toList();

        // Execute each root and its subtree
        for (GraphNode root : roots) {
            executeNode(root, nodeMap, childrenMap, promiseMap, allResults, wfId, execId);
        }

        // Wait for all promises to complete
        List<Promise<NodeResult>> allPromises = new ArrayList<>(promiseMap.values());
        if (!allPromises.isEmpty()) {
            Promise.allOf(allPromises).get();
        }

        // Determine overall status
        boolean anyFailed = allResults.stream().anyMatch(r -> "FAILED".equals(r.getStatus()));
        return new FullExecutionResult(anyFailed ? "FAILED" : "COMPLETED", allResults);
    }

    private void executeNode(GraphNode node, Map<String, GraphNode> nodeMap,
                             Map<String, List<String>> childrenMap,
                             Map<String, Promise<NodeResult>> promiseMap,
                             List<NodeResult> allResults,
                             String wfId, String execId) {
        if (promiseMap.containsKey(node.getNodeId())) return; // Already scheduled

        String nodeType = node.getType();

        // SPLIT and JOIN are structural -- no execution needed
        if ("SPLIT".equals(nodeType) || "JOIN".equals(nodeType)) {
            Promise<NodeResult> structuralPromise;

            if ("JOIN".equals(nodeType)) {
                // Wait for all parents
                List<Promise<NodeResult>> parentPromises = node.getParentIds().stream()
                        .map(pid -> {
                            if (!promiseMap.containsKey(pid)) {
                                executeNode(nodeMap.get(pid), nodeMap, childrenMap, promiseMap, allResults, wfId, execId);
                            }
                            return promiseMap.get(pid);
                        })
                        .toList();

                structuralPromise = Promise.allOf(parentPromises).thenApply(v -> {
                    NodeResult nr = new NodeResult(node.getNodeId(), nodeType, null, 0, 0, 0, "SUCCESS", null);
                    allResults.add(nr);
                    return nr;
                });
            } else {
                // SPLIT: just a passthrough
                if (node.getParentIds() != null && !node.getParentIds().isEmpty()) {
                    String parentId = node.getParentIds().get(0);
                    if (!promiseMap.containsKey(parentId)) {
                        executeNode(nodeMap.get(parentId), nodeMap, childrenMap, promiseMap, allResults, wfId, execId);
                    }
                    structuralPromise = promiseMap.get(parentId).thenApply(parentResult -> {
                        NodeResult nr = new NodeResult(node.getNodeId(), nodeType, parentResult.getResultTable(),
                                0, 0, 0, "SUCCESS", null);
                        allResults.add(nr);
                        return nr;
                    });
                } else {
                    structuralPromise = Async.function(() -> {
                        NodeResult nr = new NodeResult(node.getNodeId(), nodeType, null, 0, 0, 0, "SUCCESS", null);
                        allResults.add(nr);
                        return nr;
                    });
                }
            }

            promiseMap.put(node.getNodeId(), structuralPromise);

            // Schedule children
            List<String> children = childrenMap.getOrDefault(node.getNodeId(), List.of());
            for (String childId : children) {
                executeNode(nodeMap.get(childId), nodeMap, childrenMap, promiseMap, allResults, wfId, execId);
            }
            return;
        }

        // For data nodes: wait for parent, then execute
        Promise<NodeResult> nodePromise;
        if (node.getParentIds() != null && !node.getParentIds().isEmpty()) {
            String parentId = node.getParentIds().get(0); // Data nodes have one parent
            if (!promiseMap.containsKey(parentId)) {
                executeNode(nodeMap.get(parentId), nodeMap, childrenMap, promiseMap, allResults, wfId, execId);
            }
            nodePromise = promiseMap.get(parentId).thenApply(parentResult ->
                    executeDataNode(node, parentResult.getResultTable(), wfId, execId, allResults));
        } else {
            // Root node -- no parent
            nodePromise = Async.function(() -> executeDataNode(node, null, wfId, execId, allResults));
        }

        promiseMap.put(node.getNodeId(), nodePromise);

        // Schedule children
        List<String> children = childrenMap.getOrDefault(node.getNodeId(), List.of());
        for (String childId : children) {
            executeNode(nodeMap.get(childId), nodeMap, childrenMap, promiseMap, allResults, wfId, execId);
        }
    }

    @SuppressWarnings("unchecked")
    private NodeResult executeDataNode(GraphNode node, String sourceTable,
                                       String wfId, String execId, List<NodeResult> allResults) {
        String targetTable = "wf_" + wfId + "_exec_" + execId + "_node_" + node.getNodeId() + "_result";
        NodeResult result;

        try {
            result = switch (node.getType()) {
                case "START_FILE_UPLOAD" -> {
                    String filePath = (String) node.getConfig().get("file_path");
                    var r = activities.fileUploadActivity(new FileUploadInput(filePath, targetTable, null));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            0, r.getRowCount(), 0, "SUCCESS", null);
                }
                case "START_QUERY" -> {
                    String rawSql = (String) node.getConfig().get("raw_sql");
                    var r = activities.startQueryActivity(new StartQueryInput(rawSql, targetTable));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            0, r.getRowCount(), 0, "SUCCESS", null);
                }
                case "FILTER" -> {
                    var config = node.getConfig();
                    var r = activities.filterActivity(new FilterInput(
                            sourceTable, targetTable,
                            (String) config.get("data_mart_table"), (String) config.get("join_key"),
                            (String) config.get("mode"), (Map<String, Object>) config.get("conditions")));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            r.getInputCount(), r.getOutputCount(), r.getFilteredCount(), "SUCCESS", null);
                }
                case "ENRICH" -> {
                    var config = node.getConfig();
                    var r = activities.enrichActivity(new EnrichInput(
                            sourceTable, targetTable,
                            (String) config.get("data_mart_table"), (String) config.get("mode"),
                            (String) config.get("join_key"),
                            config.get("select_columns") != null ? (List<String>) config.get("select_columns") : null));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            r.getInputCount(), r.getOutputCount(), 0, "SUCCESS", null);
                }
                case "STOP" -> {
                    String outputPath = (String) node.getConfig().getOrDefault("output_file_path",
                            "./outputs/wf_" + wfId + "/exec_" + execId + "/stop_" + node.getNodeId() + ".csv");
                    var r = activities.stopNodeActivity(new StopNodeInput(sourceTable, outputPath));
                    yield new NodeResult(node.getNodeId(), node.getType(), null,
                            r.getRowCount(), r.getRowCount(), 0, "SUCCESS", null);
                }
                default -> throw new RuntimeException("Unknown node type: " + node.getType());
            };
        } catch (Exception e) {
            result = new NodeResult(node.getNodeId(), node.getType(), null, 0, 0, 0, "FAILED", e.getMessage());
        }

        allResults.add(result);
        return result;
    }
}
