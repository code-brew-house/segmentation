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
        String targetTable = "wf_" + input.getWorkflowId() + "_exec_" + input.getExecutionId()
                + "_node_" + input.getNodeId() + "_result";

        return switch (input.getNodeType()) {
            case "START_FILE_UPLOAD" -> {
                String filePath = (String) input.getConfig().get("file_path");
                var result = activities.fileUploadActivity(new FileUploadInput(filePath, targetTable, null));
                yield new PreviewResult(result.getResultTable(), 0, result.getRowCount(), 0);
            }
            case "START_QUERY" -> {
                String rawSql = (String) input.getConfig().get("raw_sql");
                var result = activities.startQueryActivity(new StartQueryInput(rawSql, targetTable));
                yield new PreviewResult(result.getResultTable(), 0, result.getRowCount(), 0);
            }
            case "FILTER" -> {
                var config = input.getConfig();
                var result = activities.filterActivity(new FilterInput(
                        input.getSourceTable(), targetTable,
                        (String) config.get("data_mart_table"),
                        (String) config.get("join_key"),
                        (String) config.get("mode"),
                        (Map<String, Object>) config.get("conditions")));
                yield new PreviewResult(result.getResultTable(), result.getInputCount(),
                        result.getOutputCount(), result.getFilteredCount());
            }
            case "ENRICH" -> {
                var config = input.getConfig();
                var result = activities.enrichActivity(new EnrichInput(
                        input.getSourceTable(), targetTable,
                        (String) config.get("data_mart_table"),
                        (String) config.get("mode"),
                        (String) config.get("join_key"),
                        config.get("select_columns") != null ? (List<String>) config.get("select_columns") : null));
                yield new PreviewResult(result.getResultTable(), result.getInputCount(),
                        result.getOutputCount(), result.getAddedCount());
            }
            case "STOP" -> {
                String outputPath = (String) input.getConfig().getOrDefault("output_file_path",
                        "./outputs/wf_" + input.getWorkflowId() + "/exec_" + input.getExecutionId()
                                + "/stop_" + input.getNodeId() + ".csv");
                var result = activities.stopNodeActivity(new StopNodeInput(input.getSourceTable(), outputPath));
                yield new PreviewResult(null, result.getRowCount(), result.getRowCount(), 0);
            }
            default -> throw new RuntimeException("Unknown node type: " + input.getNodeType());
        };
    }
}
