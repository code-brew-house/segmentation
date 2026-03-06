package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilterInput {
    private String sourceTable;
    private String targetTable;
    private String dataMartTable;
    private String joinKey;
    private String mode; // "JOIN" or "SUBQUERY"
    private Map<String, Object> conditions; // recursive AND/OR structure
    private boolean distinct; // deduplicate rows in JOIN mode
}
