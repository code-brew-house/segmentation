package com.workflow.segment.controller;

import com.workflow.segment.dto.*;
import com.workflow.segment.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows/{workflowId}/nodes")
@RequiredArgsConstructor
public class NodeController {
    private final NodeService nodeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NodeResponse addNode(@PathVariable UUID workflowId, @RequestBody AddNodeRequest request) {
        return nodeService.addNode(workflowId, request);
    }

    @PutMapping("/{nodeId}")
    public NodeResponse updateNode(@PathVariable UUID workflowId, @PathVariable UUID nodeId, @RequestBody UpdateNodeRequest request) {
        return nodeService.updateNode(workflowId, nodeId, request);
    }

    @DeleteMapping("/{nodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNode(@PathVariable UUID workflowId, @PathVariable UUID nodeId) {
        nodeService.deleteNode(workflowId, nodeId);
    }
}
