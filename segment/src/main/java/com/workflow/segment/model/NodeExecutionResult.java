package com.workflow.segment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_execution_result")
@Getter @Setter @NoArgsConstructor
public class NodeExecutionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private WorkflowExecution execution;

    @Column(nullable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeType;

    private Integer inputRecordCount;
    private Integer filteredRecordCount;
    private Integer outputRecordCount;
    private String resultTableName;
    private String outputFilePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(columnDefinition = "text")
    private String errorMessage;

    private Instant startedAt;
    private Instant completedAt;
}
