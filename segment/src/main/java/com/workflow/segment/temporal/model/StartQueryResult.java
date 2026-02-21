package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StartQueryResult {
    private String resultTable;
    private int rowCount;
    private List<String> columns;
}
