package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnrichInput {
    private String sourceTable;
    private String targetTable;
    private String dataMartTable;
    private String mode; // "ADD_COLUMNS" or "ADD_RECORDS"
    private String joinKey; // for ADD_COLUMNS
    private List<String> selectColumns; // for ADD_COLUMNS
}
