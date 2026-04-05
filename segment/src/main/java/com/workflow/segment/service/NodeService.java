package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.*;
import com.workflow.segment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NodeService {
    private final SegmentWorkflowRepository workflowRepository;
    private final SegmentWorkflowNodeRepository nodeRepository;
    private final SegmentWorkflowEdgeRepository edgeRepository;

    @Transactional
    public NodeResponse addNode(UUID workflowId, AddNodeRequest request) {
        SegmentWorkflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        SegmentWorkflowNode node = new SegmentWorkflowNode();
        node.setWorkflow(wf);
        node.setType(NodeType.valueOf(request.type()));
        node.setConfig(request.config());
        node = nodeRepository.save(node);
        return toResponse(node);
    }

    @Transactional
    public NodeResponse updateNode(UUID workflowId, UUID nodeId, UpdateNodeRequest request) {
        SegmentWorkflowNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        if (request.config() != null) {
            node.setConfig(request.config());
        }
        node = nodeRepository.save(node);
        return toResponse(node);
    }

    @Transactional
    public void deleteNode(UUID workflowId, UUID nodeId) {
        if (!nodeRepository.existsById(nodeId)) {
            throw new NodeNotFoundException(nodeId);
        }
        edgeRepository.deleteBySourceNodeIdOrTargetNodeId(nodeId, nodeId);
        nodeRepository.deleteById(nodeId);
    }

    private NodeResponse toResponse(SegmentWorkflowNode node) {
        return new NodeResponse(node.getId(), node.getType(), node.getConfig());
    }
}
