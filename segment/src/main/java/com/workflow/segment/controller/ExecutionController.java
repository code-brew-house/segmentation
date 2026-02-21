package com.workflow.segment.controller;

import com.workflow.segment.dto.ExecutionHistoryResponse;
import com.workflow.segment.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {
    private final ExecutionService executionService;

    @GetMapping
    public List<ExecutionHistoryResponse> listAll() {
        return executionService.listAllExecutions();
    }
}
