package com.workflow.segment.repository;
import com.workflow.segment.model.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID> {
    List<WorkflowExecution> findByWorkflowIdOrderByStartedAtDesc(UUID workflowId);
}
