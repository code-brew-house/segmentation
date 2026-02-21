package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.*;
import com.workflow.segment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NodeService {
    private final SegmentWorkflowRepository workflowRepository;
    private final SegmentWorkflowNodeRepository nodeRepository;

    @Transactional
    public NodeResponse addNode(UUID workflowId, AddNodeRequest request) {
        SegmentWorkflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        SegmentWorkflowNode node = new SegmentWorkflowNode();
        node.setWorkflow(wf);
        node.setType(NodeType.valueOf(request.type()));
        node.setParentNodeIds(request.parentNodeIds() != null
                ? request.parentNodeIds().stream().map(UUID::fromString).toList() : List.of());
        node.setConfig(request.config());
        node.setPosition(request.position());
        node = nodeRepository.save(node);
        return toResponse(node);
    }

    @Transactional
    public NodeResponse updateNode(UUID workflowId, UUID nodeId, UpdateNodeRequest request) {
        SegmentWorkflowNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        if (request.parentNodeIds() != null) {
            node.setParentNodeIds(request.parentNodeIds().stream().map(UUID::fromString).toList());
        }
        if (request.config() != null) {
            node.setConfig(request.config());
        }
        node = nodeRepository.save(node);
        return toResponse(node);
    }

    @Transactional
    public void deleteNode(UUID workflowId, UUID nodeId) {
        deleteNodeAndDescendants(nodeId);
    }

    private void deleteNodeAndDescendants(UUID nodeId) {
        List<SegmentWorkflowNode> allNodes = nodeRepository.findAll();
        List<SegmentWorkflowNode> children = allNodes.stream()
                .filter(n -> n.getParentNodeIds().contains(nodeId)).toList();
        for (SegmentWorkflowNode child : children) {
            deleteNodeAndDescendants(child.getId());
        }
        nodeRepository.deleteById(nodeId);
    }

    private NodeResponse toResponse(SegmentWorkflowNode node) {
        return new NodeResponse(node.getId(), node.getType(), node.getParentNodeIds(), node.getConfig(), node.getPosition());
    }
}
