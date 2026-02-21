package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.NodeType;
import com.workflow.segment.model.SegmentWorkflow;
import com.workflow.segment.model.SegmentWorkflowNode;
import com.workflow.segment.model.WorkflowStatus;
import com.workflow.segment.repository.SegmentWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final SegmentWorkflowRepository workflowRepository;

    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        SegmentWorkflow wf = new SegmentWorkflow();
        wf.setName(request.name());
        wf.setCreatedBy(request.createdBy());
        wf.setStatus(WorkflowStatus.DRAFT);
        wf = workflowRepository.save(wf);
        return toResponse(wf);
    }

    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDetailResponse getWorkflow(UUID id) {
        SegmentWorkflow wf = workflowRepository.findById(id).orElseThrow(() -> new WorkflowNotFoundException(id));
        return new WorkflowDetailResponse(wf.getId(), wf.getName(), wf.getCreatedBy(), wf.getCreatedAt(), wf.getStatus(),
                wf.getNodes().stream().map(n -> new NodeResponse(n.getId(), n.getType(), n.getParentNodeIds(), n.getConfig(), n.getPosition())).toList());
    }

    @Transactional
    public void deleteWorkflow(UUID id) {
        if (!workflowRepository.existsById(id)) throw new WorkflowNotFoundException(id);
        workflowRepository.deleteById(id);
    }

    @Transactional
    public WorkflowDetailResponse saveWorkflow(UUID id, SaveWorkflowRequest request) {
        SegmentWorkflow wf = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));

        // Get current nodes
        List<SegmentWorkflowNode> existing = new ArrayList<>(wf.getNodes());
        Set<UUID> incomingIds = new HashSet<>();

        for (SaveWorkflowRequest.SaveNodeRequest nodeReq : request.nodes()) {
            UUID nodeId = nodeReq.id() != null ? UUID.fromString(nodeReq.id()) : null;
            if (nodeId != null) incomingIds.add(nodeId);

            SegmentWorkflowNode node = nodeId != null
                    ? existing.stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null)
                    : null;

            if (node == null) {
                // New node
                node = new SegmentWorkflowNode();
                node.setWorkflow(wf);
                wf.getNodes().add(node);
            }

            node.setType(NodeType.valueOf(nodeReq.type()));
            node.setParentNodeIds(nodeReq.parentNodeIds() != null
                    ? new ArrayList<>(nodeReq.parentNodeIds().stream().map(UUID::fromString).toList())
                    : new ArrayList<>());
            node.setConfig(nodeReq.config());
            node.setPosition(nodeReq.position());
        }

        // Delete nodes not in incoming set
        List<SegmentWorkflowNode> toRemove = existing.stream()
                .filter(n -> !incomingIds.contains(n.getId()))
                .toList();
        wf.getNodes().removeAll(toRemove);

        workflowRepository.save(wf);
        return getWorkflow(id);
    }

    private WorkflowResponse toResponse(SegmentWorkflow wf) {
        return new WorkflowResponse(wf.getId(), wf.getName(), wf.getCreatedBy(), wf.getCreatedAt(), wf.getStatus(), wf.getNodes().size());
    }
}
