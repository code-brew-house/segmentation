package com.workflow.segment.repository;
import com.workflow.segment.model.SegmentWorkflowNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface SegmentWorkflowNodeRepository extends JpaRepository<SegmentWorkflowNode, UUID> {
    List<SegmentWorkflowNode> findByWorkflowId(UUID workflowId);
}
