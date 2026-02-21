package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadInput {
    private String filePath;
    private String targetTable;
    private Map<String, String> schemaMapping; // optional
}
