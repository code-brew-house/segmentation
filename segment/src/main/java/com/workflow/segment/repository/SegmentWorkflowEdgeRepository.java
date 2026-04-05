package com.workflow.segment.repository;

import com.workflow.segment.model.SegmentWorkflowEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SegmentWorkflowEdgeRepository extends JpaRepository<SegmentWorkflowEdge, UUID> {
    List<SegmentWorkflowEdge> findByWorkflowId(UUID workflowId);
    List<SegmentWorkflowEdge> findBySourceNodeIdOrTargetNodeId(UUID sourceNodeId, UUID targetNodeId);
    void deleteBySourceNodeIdOrTargetNodeId(UUID sourceNodeId, UUID targetNodeId);
}
