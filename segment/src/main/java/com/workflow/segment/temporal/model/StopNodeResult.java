package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StopNodeResult {
    private String filePath;
    private int rowCount;
}
