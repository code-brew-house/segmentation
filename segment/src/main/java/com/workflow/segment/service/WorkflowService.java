package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.*;
import com.workflow.segment.repository.SegmentWorkflowEdgeRepository;
import com.workflow.segment.repository.SegmentWorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final SegmentWorkflowRepository workflowRepository;
    private final SegmentWorkflowEdgeRepository edgeRepository;

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
        SegmentWorkflow wf = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        List<NodeResponse> nodeResponses = wf.getNodes().stream()
                .map(n -> new NodeResponse(n.getId(), n.getType(), n.getConfig()))
                .toList();
        List<EdgeResponse> edgeResponses = wf.getEdges().stream()
                .map(e -> new EdgeResponse(e.getId(), e.getName(),
                        e.getSourceNode().getId(), e.getTargetNode().getId(),
                        e.getSourceHandle(), e.getTargetHandle(),
                        e.getCondition(), e.isDefault(), e.getSortOrder(), e.getMetadata()))
                .toList();
        return new WorkflowDetailResponse(wf.getId(), wf.getName(), wf.getCreatedBy(),
                wf.getCreatedAt(), wf.getStatus(), nodeResponses, edgeResponses);
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

        // --- Upsert nodes ---
        List<SegmentWorkflowNode> existingNodes = new ArrayList<>(wf.getNodes());
        Set<UUID> incomingNodeIds = new HashSet<>();
        // Track new (non-UUID) client IDs in order for post-flush mapping
        List<String> newClientIds = new ArrayList<>();

        for (SaveWorkflowRequest.SaveNodeRequest nodeReq : request.nodes()) {
            UUID nodeId = nodeReq.id() != null ? tryParseUuid(nodeReq.id()) : null;
            if (nodeId != null) incomingNodeIds.add(nodeId);

            SegmentWorkflowNode node = nodeId != null
                    ? existingNodes.stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null)
                    : null;

            if (node == null) {
                node = new SegmentWorkflowNode();
                node.setWorkflow(wf);
                wf.getNodes().add(node);
                newClientIds.add(nodeReq.id());
            }

            node.setType(NodeType.valueOf(nodeReq.type()));
            node.setConfig(nodeReq.config());
        }

        // Delete nodes not in incoming set
        List<SegmentWorkflowNode> nodesToRemove = existingNodes.stream()
                .filter(n -> !incomingNodeIds.contains(n.getId()))
                .toList();
        wf.getNodes().removeAll(nodesToRemove);

        // Flush to persist new nodes and generate UUIDs
        workflowRepository.saveAndFlush(wf);

        // Build maps: existing UUID-based nodes are already mapped; new nodes need client ID mapping
        Map<String, UUID> clientIdToUuid = new HashMap<>();
        Map<UUID, SegmentWorkflowNode> nodeMap = new HashMap<>();

        // All persisted nodes get UUID-string mapping
        for (SegmentWorkflowNode n : wf.getNodes()) {
            nodeMap.put(n.getId(), n);
            clientIdToUuid.put(n.getId().toString(), n.getId());
        }

        // Map new client IDs to the newly created nodes (those whose UUID is not in incomingNodeIds)
        List<SegmentWorkflowNode> newPersistedNodes = wf.getNodes().stream()
                .filter(n -> !incomingNodeIds.contains(n.getId()))
                .toList();
        for (int i = 0; i < newClientIds.size() && i < newPersistedNodes.size(); i++) {
            String clientId = newClientIds.get(i);
            if (clientId != null) {
                clientIdToUuid.put(clientId, newPersistedNodes.get(i).getId());
            }
        }

        // --- Validate edges ---
        List<SaveEdgeRequest> edgeRequests = request.edges() != null ? request.edges() : List.of();
        for (SaveEdgeRequest edgeReq : edgeRequests) {
            UUID sourceId = clientIdToUuid.get(edgeReq.source());
            UUID targetId = clientIdToUuid.get(edgeReq.target());

            if (sourceId == null || !nodeMap.containsKey(sourceId)) {
                throw new InvalidEdgeException("Edge source '" + edgeReq.source() + "' does not reference a node in this workflow");
            }
            if (targetId == null || !nodeMap.containsKey(targetId)) {
                throw new InvalidEdgeException("Edge target '" + edgeReq.target() + "' does not reference a node in this workflow");
            }
            if (sourceId.equals(targetId)) {
                throw new InvalidEdgeException("Self-referencing edge: source and target are the same node '" + edgeReq.source() + "'");
            }
        }

        // --- Upsert edges ---
        List<SegmentWorkflowEdge> existingEdges = new ArrayList<>(wf.getEdges());
        Set<UUID> incomingEdgeIds = new HashSet<>();

        for (SaveEdgeRequest edgeReq : edgeRequests) {
            UUID edgeId = edgeReq.id() != null ? tryParseUuid(edgeReq.id()) : null;
            if (edgeId != null) incomingEdgeIds.add(edgeId);

            SegmentWorkflowEdge edge = edgeId != null
                    ? existingEdges.stream().filter(e -> e.getId().equals(edgeId)).findFirst().orElse(null)
                    : null;

            if (edge == null) {
                edge = new SegmentWorkflowEdge();
                edge.setWorkflow(wf);
                wf.getEdges().add(edge);
            }

            UUID sourceId = clientIdToUuid.get(edgeReq.source());
            UUID targetId = clientIdToUuid.get(edgeReq.target());

            edge.setName(edgeReq.name());
            edge.setSourceNode(nodeMap.get(sourceId));
            edge.setTargetNode(nodeMap.get(targetId));
            edge.setSourceHandle(edgeReq.sourceHandle());
            edge.setTargetHandle(edgeReq.targetHandle());
            edge.setCondition(edgeReq.condition());
            edge.setDefault(edgeReq.isDefault());
            edge.setSortOrder(edgeReq.sortOrder());
            edge.setMetadata(edgeReq.metadata());
        }

        // Delete edges not in incoming set
        List<SegmentWorkflowEdge> edgesToRemove = existingEdges.stream()
                .filter(e -> !incomingEdgeIds.contains(e.getId()))
                .toList();
        wf.getEdges().removeAll(edgesToRemove);

        workflowRepository.save(wf);
        return getWorkflow(id);
    }

    private UUID tryParseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private WorkflowResponse toResponse(SegmentWorkflow wf) {
        return new WorkflowResponse(wf.getId(), wf.getName(), wf.getCreatedBy(),
                wf.getCreatedAt(), wf.getStatus(), wf.getNodes().size());
    }
}
