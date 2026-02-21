package com.workflow.segment.service;

import com.workflow.segment.dto.SqlPreviewResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SqlPreviewService {

    @SuppressWarnings("unchecked")
    public SqlPreviewResponse generatePreview(String nodeType, Map<String, Object> config) {
        String sql = switch (nodeType) {
            case "START_QUERY" -> generateStartQuerySql(config);
            case "FILTER" -> generateFilterSql(config);
            case "ENRICH" -> generateEnrichSql(config);
            default -> throw new IllegalArgumentException("SQL preview not supported for node type: " + nodeType);
        };
        return new SqlPreviewResponse(sql);
    }

    private String generateStartQuerySql(Map<String, Object> config) {
        String rawSql = (String) config.getOrDefault("raw_sql", "");
        if (rawSql.isBlank()) return "-- No SQL query configured";
        return rawSql;
    }

    @SuppressWarnings("unchecked")
    private String generateFilterSql(Map<String, Object> config) {
        String dmTable = (String) config.getOrDefault("data_mart_table", "<data_mart_table>");
        String mode = (String) config.getOrDefault("mode", "JOIN");
        String joinKey = (String) config.getOrDefault("join_key", "<join_key>");
        Map<String, Object> conditions = (Map<String, Object>) config.get("conditions");
        String whereClause = SqlConditionBuilder.buildWhereClause(conditions);

        if ("JOIN".equalsIgnoreCase(mode)) {
            return String.format(
                    "SELECT src.*\nFROM <parent_output> src\nJOIN %s dm\n  ON src.%s = dm.%s\n%s",
                    dmTable, joinKey, joinKey, whereClause);
        } else {
            return String.format(
                    "SELECT *\nFROM <parent_output>\nWHERE %s IN (\n  SELECT %s FROM %s\n  %s\n)",
                    joinKey, joinKey, dmTable, whereClause);
        }
    }

    @SuppressWarnings("unchecked")
    private String generateEnrichSql(Map<String, Object> config) {
        String dmTable = (String) config.getOrDefault("data_mart_table", "<data_mart_table>");
        String mode = (String) config.getOrDefault("mode", "ADD_COLUMNS");
        String joinKey = (String) config.getOrDefault("join_key", "<join_key>");
        List<String> selectColumns = (List<String>) config.get("select_columns");

        if ("ADD_COLUMNS".equalsIgnoreCase(mode)) {
            String dmCols = selectColumns != null && !selectColumns.isEmpty()
                    ? selectColumns.stream().map(c -> "dm." + c).collect(Collectors.joining(", "))
                    : "dm.*";
            return String.format(
                    "SELECT src.*, %s\nFROM <parent_output> src\nLEFT JOIN %s dm\n  ON src.%s = dm.%s",
                    dmCols, dmTable, joinKey, joinKey);
        } else {
            return String.format(
                    "SELECT * FROM <parent_output>\nUNION ALL\nSELECT * FROM %s",
                    dmTable);
        }
    }
}
