package com.workflow.segment.repository;
import com.workflow.segment.model.SegmentWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SegmentWorkflowRepository extends JpaRepository<SegmentWorkflow, UUID> {}
