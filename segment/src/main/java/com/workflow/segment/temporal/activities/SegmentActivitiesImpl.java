package com.workflow.segment.temporal.activities;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.workflow.segment.model.ExecutionStatus;
import com.workflow.segment.model.NodeExecutionResult;
import com.workflow.segment.model.WorkflowExecution;
import com.workflow.segment.repository.NodeExecutionResultRepository;
import com.workflow.segment.repository.WorkflowExecutionRepository;
import com.workflow.segment.service.SqlConditionBuilder;
import com.workflow.segment.temporal.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SegmentActivitiesImpl implements SegmentActivities {

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowExecutionRepository executionRepository;
    private final NodeExecutionResultRepository nodeExecutionResultRepository;

    @Override
    public FileUploadResult fileUploadActivity(FileUploadInput input) {
        String table = input.getTargetTable();
        log.info("FileUpload: ingesting {} into {}", input.getFilePath(), table);

        try (CSVReader reader = new CSVReader(new FileReader(input.getFilePath()))) {
            String[] headers = reader.readNext();
            if (headers == null) throw new RuntimeException("Empty CSV file");

            // Clean headers
            List<String> columns = Arrays.stream(headers)
                    .map(h -> h.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_"))
                    .toList();

            // Drop and create table (all columns as TEXT for POC)
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
            String colDefs = columns.stream().map(c -> c + " TEXT").collect(Collectors.joining(", "));
            jdbcTemplate.execute("CREATE TABLE " + table + " (" + colDefs + ")");

            // Bulk insert
            String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
            String insertSql = "INSERT INTO " + table + " VALUES (" + placeholders + ")";
            String[] row;
            int count = 0;
            while ((row = reader.readNext()) != null) {
                jdbcTemplate.update(insertSql, (Object[]) row);
                count++;
            }

            return new FileUploadResult(table, count, columns);
        } catch (Exception e) {
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public StartQueryResult startQueryActivity(StartQueryInput input) {
        String table = input.getTargetTable();
        log.info("StartQuery: executing into {}", table);

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
        jdbcTemplate.execute("CREATE TABLE " + table + " AS " + input.getRawSql());

        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        List<String> columns = getColumns(table);

        return new StartQueryResult(table, count, columns);
    }

    @Override
    public FilterResult filterActivity(FilterInput input) {
        String source = input.getSourceTable();
        String target = input.getTargetTable();
        String dmTable = input.getDataMartTable();
        log.info("Filter: {} + {} -> {}", source, dmTable, target);

        int inputCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + source, Integer.class);

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + target);

        String whereClause = SqlConditionBuilder.buildWhereClause(input.getConditions());

        if ("JOIN".equalsIgnoreCase(input.getMode())) {
            String sql = String.format(
                    "CREATE TABLE %s AS SELECT src.* FROM %s src JOIN %s dm ON src.%s::text = dm.%s::text %s",
                    target, source, dmTable, input.getJoinKey(), input.getJoinKey(),
                    whereClause);
            jdbcTemplate.execute(sql);
        } else {
            // SUBQUERY mode
            String sql = String.format(
                    "CREATE TABLE %s AS SELECT * FROM %s WHERE %s::text IN (SELECT %s::text FROM %s %s)",
                    target, source, input.getJoinKey(), input.getJoinKey(), dmTable, whereClause);
            jdbcTemplate.execute(sql);
        }

        int outputCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + target, Integer.class);
        int filteredCount = inputCount - outputCount;

        return new FilterResult(target, inputCount, filteredCount, outputCount);
    }

    @Override
    public EnrichResult enrichActivity(EnrichInput input) {
        String source = input.getSourceTable();
        String target = input.getTargetTable();
        String dmTable = input.getDataMartTable();
        log.info("Enrich: {} + {} -> {} (mode: {})", source, dmTable, target, input.getMode());

        int inputCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + source, Integer.class);

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + target);

        if ("ADD_COLUMNS".equalsIgnoreCase(input.getMode())) {
            String dmCols = input.getSelectColumns() != null && !input.getSelectColumns().isEmpty()
                    ? input.getSelectColumns().stream().map(c -> "dm." + c).collect(Collectors.joining(", "))
                    : "dm.*";
            String sql = String.format(
                    "CREATE TABLE %s AS SELECT src.*, %s FROM %s src LEFT JOIN %s dm ON src.%s::text = dm.%s::text",
                    target, dmCols, source, dmTable, input.getJoinKey(), input.getJoinKey());
            jdbcTemplate.execute(sql);
        } else {
            // ADD_RECORDS mode - UNION ALL
            String sql = String.format(
                    "CREATE TABLE %s AS SELECT * FROM %s UNION ALL SELECT * FROM %s",
                    target, source, dmTable);
            jdbcTemplate.execute(sql);
        }

        int outputCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + target, Integer.class);
        int addedCount = outputCount - inputCount;

        return new EnrichResult(target, inputCount, addedCount, outputCount);
    }

    @Override
    public StopNodeResult stopNodeActivity(StopNodeInput input) {
        String source = input.getSourceTable();
        String outputPath = input.getOutputFilePath();
        log.info("StopNode: exporting {} to {}", source, outputPath);

        // Ensure parent directories exist
        Path path = Path.of(outputPath);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create output directory", e);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + source);
        if (rows.isEmpty()) {
            // Write empty CSV
            try (CSVWriter writer = new CSVWriter(new FileWriter(outputPath))) {
                List<String> columns = getColumns(source);
                writer.writeNext(columns.toArray(new String[0]));
            } catch (IOException e) {
                throw new RuntimeException("CSV write failed", e);
            }
            return new StopNodeResult(outputPath, 0);
        }

        try (CSVWriter writer = new CSVWriter(new FileWriter(outputPath))) {
            String[] headers = rows.get(0).keySet().toArray(new String[0]);
            writer.writeNext(headers);
            for (Map<String, Object> row : rows) {
                String[] values = row.values().stream()
                        .map(v -> v == null ? "" : v.toString())
                        .toArray(String[]::new);
                writer.writeNext(values);
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV write failed", e);
        }

        return new StopNodeResult(outputPath, rows.size());
    }

    @Override
    @Transactional
    public void completeExecution(String executionId, List<NodeResult> nodeResults, String finalStatus) {
        UUID execId = UUID.fromString(executionId);
        WorkflowExecution execution = executionRepository.findById(execId)
                .orElseThrow(() -> new RuntimeException("Execution not found: " + executionId));

        // Persist each node result
        for (NodeResult nr : nodeResults) {
            NodeExecutionResult entity = new NodeExecutionResult();
            entity.setExecution(execution);
            entity.setNodeId(UUID.fromString(nr.getNodeId()));
            entity.setNodeType(com.workflow.segment.model.NodeType.valueOf(nr.getNodeType()));
            entity.setStatus(ExecutionStatus.valueOf(nr.getStatus()));
            entity.setInputRecordCount(nr.getInputCount());
            entity.setFilteredRecordCount(nr.getFilteredCount());
            entity.setOutputRecordCount(nr.getOutputCount());
            entity.setResultTableName(nr.getResultTable());
            entity.setOutputFilePath(nr.getOutputFilePath());
            entity.setErrorMessage(nr.getErrorMessage());
            entity.setStartedAt(Instant.now());
            entity.setCompletedAt(Instant.now());
            nodeExecutionResultRepository.save(entity);
        }

        // Update execution status
        execution.setStatus(ExecutionStatus.valueOf(finalStatus));
        execution.setCompletedAt(Instant.now());
        executionRepository.save(execution);
        log.info("Execution {} marked as {}", executionId, finalStatus);
    }

    private List<String> getColumns(String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) ORDER BY ordinal_position",
                String.class, tableName);
    }
}
