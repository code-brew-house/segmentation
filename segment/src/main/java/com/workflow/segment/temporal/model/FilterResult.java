package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilterResult {
    private String resultTable;
    private int inputCount;
    private int filteredCount;
    private int outputCount;
}
