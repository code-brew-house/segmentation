package com.workflow.segment.repository;
import com.workflow.segment.model.ExecutionStatus;
import com.workflow.segment.model.NodeExecutionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface NodeExecutionResultRepository extends JpaRepository<NodeExecutionResult, UUID> {
    List<NodeExecutionResult> findByExecutionId(UUID executionId);
    Optional<NodeExecutionResult> findByExecutionIdAndNodeId(UUID executionId, UUID nodeId);
    Optional<NodeExecutionResult> findTopByNodeIdAndStatusOrderByStartedAtDesc(UUID nodeId, ExecutionStatus status);
}
