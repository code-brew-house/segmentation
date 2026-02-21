package com.workflow.segment.temporal.workflows;

import com.workflow.segment.temporal.activities.SegmentActivities;
import com.workflow.segment.temporal.model.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class PreviewWorkflowImpl implements PreviewWorkflow {

    private final SegmentActivities activities = Workflow.newActivityStub(
            SegmentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    @Override
    @SuppressWarnings("unchecked")
    public PreviewResult execute(PreviewInput input) {
        // Use first 8 chars of each UUID to keep table names within PostgreSQL's 63-char limit
        String wfId = input.getWorkflowId().replace("-", "").substring(0, 8);
        String execId = input.getExecutionId().replace("-", "").substring(0, 8);
        String nodeId = input.getNodeId().replace("-", "").substring(0, 8);
        String targetTable = "wf_" + wfId + "_e_" + execId + "_n_" + nodeId + "_r";

        PreviewResult previewResult;
        NodeResult nodeResult;

        switch (input.getNodeType()) {
            case "START_FILE_UPLOAD" -> {
                String filePath = (String) input.getConfig().get("file_path");
                var r = activities.fileUploadActivity(new FileUploadInput(filePath, targetTable, null));
                previewResult = new PreviewResult(r.getResultTable(), 0, r.getRowCount(), 0);
                nodeResult = new NodeResult(input.getNodeId(), input.getNodeType(),
                        r.getResultTable(), 0, r.getRowCount(), 0, "SUCCESS", null, null);
            }
            case "START_QUERY" -> {
                String rawSql = (String) input.getConfig().get("raw_sql");
                var r = activities.startQueryActivity(new StartQueryInput(rawSql, targetTable));
                previewResult = new PreviewResult(r.getResultTable(), 0, r.getRowCount(), 0);
                nodeResult = new NodeResult(input.getNodeId(), input.getNodeType(),
                        r.getResultTable(), 0, r.getRowCount(), 0, "SUCCESS", null, null);
            }
            case "FILTER" -> {
                var config = input.getConfig();
                var r = activities.filterActivity(new FilterInput(
                        input.getSourceTable(), targetTable,
                        (String) config.get("data_mart_table"), (String) config.get("join_key"),
                        (String) config.get("mode"), (Map<String, Object>) config.get("conditions")));
                previewResult = new PreviewResult(r.getResultTable(), r.getInputCount(), r.getOutputCount(), r.getFilteredCount());
                nodeResult = new NodeResult(input.getNodeId(), input.getNodeType(),
                        r.getResultTable(), r.getInputCount(), r.getOutputCount(), r.getFilteredCount(), "SUCCESS", null, null);
            }
            case "ENRICH" -> {
                var config = input.getConfig();
                var r = activities.enrichActivity(new EnrichInput(
                        input.getSourceTable(), targetTable,
                        (String) config.get("data_mart_table"), (String) config.get("mode"),
                        (String) config.get("join_key"),
                        config.get("select_columns") != null ? (List<String>) config.get("select_columns") : null));
                previewResult = new PreviewResult(r.getResultTable(), r.getInputCount(), r.getOutputCount(), r.getAddedCount());
                nodeResult = new NodeResult(input.getNodeId(), input.getNodeType(),
                        r.getResultTable(), r.getInputCount(), r.getOutputCount(), r.getAddedCount(), "SUCCESS", null, null);
            }
            case "STOP" -> {
                Object rawPath = input.getConfig() != null ? input.getConfig().get("output_file_path") : null;
                String outputPath = (rawPath instanceof String s && !s.isBlank()) ? s
                        : "./outputs/wf_" + wfId + "/exec_" + execId + "/stop_" + nodeId + ".csv";

                var r = activities.stopNodeActivity(new StopNodeInput(input.getSourceTable(), outputPath));
                previewResult = new PreviewResult(null, r.getRowCount(), r.getRowCount(), 0);
                nodeResult = new NodeResult(input.getNodeId(), input.getNodeType(),
                        null, r.getRowCount(), r.getRowCount(), 0, "SUCCESS", null, r.getFilePath());
            }
            default -> throw new RuntimeException("Unknown node type: " + input.getNodeType());
        }

        // Persist result and mark execution SUCCESS in DB
        activities.completeExecution(input.getExecutionId(), List.of(nodeResult), "SUCCESS");
        return previewResult;
    }
}
