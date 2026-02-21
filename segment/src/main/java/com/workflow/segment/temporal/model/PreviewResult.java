package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreviewResult {
    private String resultTable;
    private int inputCount;
    private int outputCount;
    private int filteredCount;
}
