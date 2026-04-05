# Explicit Graph Edges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce `SegmentWorkflowEdge` as a first-class JPA entity and `GraphEdge` as a Temporal runtime model, making edges the single source of truth for graph topology. Remove `parentNodeIds` and `position` from nodes.

**Architecture:** Edges become explicit entities with metadata (name, condition, handles, sortOrder). Nodes become pure — no topology awareness. The Temporal workflow traverses the DAG using edge maps instead of parent ID lists. SPLIT condition routing moves from node config to edge conditions evaluated in sortOrder with a default/else fallback.

**Tech Stack:** Spring Boot 3.5.x, JPA/Hibernate, PostgreSQL, Temporal SDK, JUnit 5, H2 (test), Lombok

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/workflow/segment/model/SegmentWorkflowEdge.java` | JPA edge entity |
| Create | `src/main/java/com/workflow/segment/repository/SegmentWorkflowEdgeRepository.java` | Edge JPA repository |
| Create | `src/main/java/com/workflow/segment/temporal/model/GraphEdge.java` | Temporal runtime edge model |
| Create | `src/main/java/com/workflow/segment/dto/EdgeResponse.java` | Edge response DTO |
| Create | `src/main/java/com/workflow/segment/dto/SaveEdgeRequest.java` | Edge save request DTO |
| Modify | `src/main/java/com/workflow/segment/model/SegmentWorkflowNode.java` | Remove `parentNodeIds`, `position` |
| Modify | `src/main/java/com/workflow/segment/model/SegmentWorkflow.java` | Add `edges` relationship |
| Modify | `src/main/java/com/workflow/segment/temporal/model/GraphNode.java` | Remove `parentIds` |
| Modify | `src/main/java/com/workflow/segment/temporal/model/FullExecutionInput.java` | Add `edges` field |
| Modify | `src/main/java/com/workflow/segment/dto/SaveWorkflowRequest.java` | Add edges, remove parentNodeIds/position from SaveNodeRequest |
| Modify | `src/main/java/com/workflow/segment/dto/WorkflowDetailResponse.java` | Add edges |
| Modify | `src/main/java/com/workflow/segment/dto/NodeResponse.java` | Remove `parentNodeIds`, `position` |
| Modify | `src/main/java/com/workflow/segment/dto/AddNodeRequest.java` | Remove `parentNodeIds`, `position` |
| Modify | `src/main/java/com/workflow/segment/dto/UpdateNodeRequest.java` | Remove `parentNodeIds` |
| Modify | `src/main/java/com/workflow/segment/service/WorkflowService.java` | Edge upsert + validation in `saveWorkflow()` |
| Modify | `src/main/java/com/workflow/segment/service/NodeService.java` | Edge-aware delete, remove parentNodeIds handling |
| Modify | `src/main/java/com/workflow/segment/service/ExecutionService.java` | Build `GraphEdge` list, edge-aware preview |
| Modify | `src/main/java/com/workflow/segment/temporal/workflows/FullExecutionWorkflowImpl.java` | Edge-based DAG traversal |
| Modify | `src/test/java/com/workflow/segment/controller/WorkflowControllerTest.java` | Update for edges in save/get |
| Modify | `src/test/java/com/workflow/segment/controller/NodeControllerTest.java` | Update for removed parentNodeIds |
| Create | `src/test/java/com/workflow/segment/service/WorkflowServiceEdgeValidationTest.java` | Edge validation tests |

---

### Task 1: Create `SegmentWorkflowEdge` JPA Entity

**Files:**
- Create: `src/main/java/com/workflow/segment/model/SegmentWorkflowEdge.java`

- [ ] **Step 1: Create the edge entity**

```java
package com.workflow.segment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "segment_workflow_edge")
@Getter @Setter @NoArgsConstructor
public class SegmentWorkflowEdge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    @JsonIgnore
    private SegmentWorkflow workflow;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_id", nullable = false)
    private SegmentWorkflowNode sourceNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_node_id", nullable = false)
    private SegmentWorkflowNode targetNode;

    private String sourceHandle;
    private String targetHandle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private Map<String, Object> condition;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    private Integer sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private Map<String, Object> metadata;
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/workflow/segment/model/SegmentWorkflowEdge.java
git commit -m "feat: add SegmentWorkflowEdge JPA entity"
```

---

### Task 2: Create `SegmentWorkflowEdgeRepository`

**Files:**
- Create: `src/main/java/com/workflow/segment/repository/SegmentWorkflowEdgeRepository.java`

- [ ] **Step 1: Create the repository interface**

```java
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
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/workflow/segment/repository/SegmentWorkflowEdgeRepository.java
git commit -m "feat: add SegmentWorkflowEdgeRepository"
```

---

### Task 3: Add `edges` to `SegmentWorkflow`, Remove `parentNodeIds`/`position` from `SegmentWorkflowNode`

**Files:**
- Modify: `src/main/java/com/workflow/segment/model/SegmentWorkflow.java:33-34`
- Modify: `src/main/java/com/workflow/segment/model/SegmentWorkflowNode.java:32-41`

- [ ] **Step 1: Add edges relationship to `SegmentWorkflow`**

In `SegmentWorkflow.java`, add after the `nodes` field (line 34):

```java
@OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
private List<SegmentWorkflowEdge> edges = new ArrayList<>();
```

Add import:
```java
import com.workflow.segment.model.SegmentWorkflowEdge;
```

- [ ] **Step 2: Remove `parentNodeIds` and `position` from `SegmentWorkflowNode`**

In `SegmentWorkflowNode.java`, remove lines 32-41 (the `@ElementCollection` block for `parentNodeIds` and the `position` field):

```java
// REMOVE these lines:
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "node_parent_ids", joinColumns = @JoinColumn(name = "node_id"))
@Column(name = "parent_node_id")
private List<UUID> parentNodeIds = new ArrayList<>();

// REMOVE:
private Integer position;
```

Remove unused imports: `java.util.ArrayList`, `java.util.List` (if no longer needed).

The resulting `SegmentWorkflowNode.java` should be:

```java
package com.workflow.segment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "segment_workflow_node")
@Getter @Setter @NoArgsConstructor
public class SegmentWorkflowNode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    @JsonIgnore
    private SegmentWorkflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private Map<String, Object> config;
}
```

- [ ] **Step 3: Verify the project compiles (expect failures — downstream code still references removed fields)**

Run: `./gradlew compileJava`
Expected: FAIL — `getParentNodeIds()`, `setParentNodeIds()`, `getPosition()`, `setPosition()` no longer exist. This is expected; we'll fix callers in subsequent tasks.

- [ ] **Step 4: Commit (compile-broken is OK at this stage — we're doing a coordinated multi-file change)**

```bash
git add src/main/java/com/workflow/segment/model/SegmentWorkflow.java src/main/java/com/workflow/segment/model/SegmentWorkflowNode.java
git commit -m "feat: add edges to SegmentWorkflow, remove parentNodeIds/position from node"
```

---

### Task 4: Create `GraphEdge` Temporal Model, Update `GraphNode` and `FullExecutionInput`

**Files:**
- Create: `src/main/java/com/workflow/segment/temporal/model/GraphEdge.java`
- Modify: `src/main/java/com/workflow/segment/temporal/model/GraphNode.java`
- Modify: `src/main/java/com/workflow/segment/temporal/model/FullExecutionInput.java`

- [ ] **Step 1: Create `GraphEdge`**

```java
package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphEdge {
    private String edgeId;
    private String sourceNodeId;
    private String targetNodeId;
    private String name;
    private Map<String, Object> condition;
    private boolean isDefault;
    private int sortOrder;
}
```

- [ ] **Step 2: Remove `parentIds` from `GraphNode`**

Replace the entire `GraphNode.java` with:

```java
package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphNode {
    private String nodeId;
    private String type;
    private Map<String, Object> config;
}
```

- [ ] **Step 3: Add `edges` to `FullExecutionInput`**

Replace the entire `FullExecutionInput.java` with:

```java
package com.workflow.segment.temporal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FullExecutionInput {
    private String workflowId;
    private String executionId;
    private List<GraphNode> graph;
    private List<GraphEdge> edges;
}
```

- [ ] **Step 4: Verify the project compiles (expect failures — downstream code references removed `parentIds`)**

Run: `./gradlew compileJava`
Expected: FAIL in `FullExecutionWorkflowImpl.java` and `ExecutionService.java`. Expected; fixed in later tasks.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/workflow/segment/temporal/model/GraphEdge.java \
    src/main/java/com/workflow/segment/temporal/model/GraphNode.java \
    src/main/java/com/workflow/segment/temporal/model/FullExecutionInput.java
git commit -m "feat: add GraphEdge, remove parentIds from GraphNode, add edges to FullExecutionInput"
```

---

### Task 5: Update DTOs — EdgeResponse, SaveEdgeRequest, and Modified Records

**Files:**
- Create: `src/main/java/com/workflow/segment/dto/EdgeResponse.java`
- Create: `src/main/java/com/workflow/segment/dto/SaveEdgeRequest.java`
- Modify: `src/main/java/com/workflow/segment/dto/SaveWorkflowRequest.java`
- Modify: `src/main/java/com/workflow/segment/dto/WorkflowDetailResponse.java`
- Modify: `src/main/java/com/workflow/segment/dto/NodeResponse.java`
- Modify: `src/main/java/com/workflow/segment/dto/AddNodeRequest.java`
- Modify: `src/main/java/com/workflow/segment/dto/UpdateNodeRequest.java`

- [ ] **Step 1: Create `EdgeResponse`**

```java
package com.workflow.segment.dto;

import java.util.Map;
import java.util.UUID;

public record EdgeResponse(UUID id, String name, UUID sourceNodeId, UUID targetNodeId,
                           String sourceHandle, String targetHandle,
                           Map<String, Object> condition, boolean isDefault,
                           Integer sortOrder, Map<String, Object> metadata) {}
```

- [ ] **Step 2: Create `SaveEdgeRequest`**

```java
package com.workflow.segment.dto;

import java.util.Map;

public record SaveEdgeRequest(String id, String name, String source, String target,
                              String sourceHandle, String targetHandle,
                              Map<String, Object> condition, boolean isDefault,
                              Integer sortOrder, Map<String, Object> metadata) {}
```

- [ ] **Step 3: Update `SaveWorkflowRequest`**

Replace entire file:

```java
package com.workflow.segment.dto;

import java.util.List;
import java.util.Map;

public record SaveWorkflowRequest(List<SaveNodeRequest> nodes, List<SaveEdgeRequest> edges) {
    public record SaveNodeRequest(String id, String type, Map<String, Object> config) {}
}
```

- [ ] **Step 4: Update `WorkflowDetailResponse`**

Replace entire file:

```java
package com.workflow.segment.dto;

import com.workflow.segment.model.WorkflowStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowDetailResponse(UUID id, String name, String createdBy, Instant createdAt,
                                      WorkflowStatus status, List<NodeResponse> nodes,
                                      List<EdgeResponse> edges) {}
```

- [ ] **Step 5: Update `NodeResponse`**

Replace entire file:

```java
package com.workflow.segment.dto;

import com.workflow.segment.model.NodeType;

import java.util.Map;
import java.util.UUID;

public record NodeResponse(UUID id, NodeType type, Map<String, Object> config) {}
```

- [ ] **Step 6: Update `AddNodeRequest`**

Replace entire file:

```java
package com.workflow.segment.dto;

import java.util.Map;

public record AddNodeRequest(String type, Map<String, Object> config) {}
```

- [ ] **Step 7: Update `UpdateNodeRequest`**

Replace entire file:

```java
package com.workflow.segment.dto;

import java.util.Map;

public record UpdateNodeRequest(Map<String, Object> config) {}
```

- [ ] **Step 8: Verify the project compiles (expect failures in services)**

Run: `./gradlew compileJava`
Expected: FAIL in `WorkflowService`, `NodeService`, `ExecutionService` — they reference old DTO fields. Fixed next.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/workflow/segment/dto/EdgeResponse.java \
    src/main/java/com/workflow/segment/dto/SaveEdgeRequest.java \
    src/main/java/com/workflow/segment/dto/SaveWorkflowRequest.java \
    src/main/java/com/workflow/segment/dto/WorkflowDetailResponse.java \
    src/main/java/com/workflow/segment/dto/NodeResponse.java \
    src/main/java/com/workflow/segment/dto/AddNodeRequest.java \
    src/main/java/com/workflow/segment/dto/UpdateNodeRequest.java
git commit -m "feat: add edge DTOs, remove parentNodeIds/position from node DTOs"
```

---

### Task 6: Update `WorkflowService` — Edge Upsert and Validation

**Files:**
- Modify: `src/main/java/com/workflow/segment/service/WorkflowService.java`

- [ ] **Step 1: Write failing test for edge validation**

Create `src/test/java/com/workflow/segment/service/WorkflowServiceEdgeValidationTest.java`:

```java
package com.workflow.segment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.segment.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowServiceEdgeValidationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String createWorkflow() throws Exception {
        var result = mockMvc.perform(post("/api/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("Edge Test", "tester"))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void shouldSaveWorkflowWithNodesAndEdges() throws Exception {
        String wfId = createWorkflow();

        var request = new SaveWorkflowRequest(
                List.of(
                        new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1")),
                        new SaveWorkflowRequest.SaveNodeRequest("n2", "STOP", Map.of())
                ),
                List.of(
                        new SaveEdgeRequest(null, "flow", "n1", "n2", null, null, null, false, null, null)
                )
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].name").value("flow"));
    }

    @Test
    void shouldRejectSelfReferencingEdge() throws Exception {
        String wfId = createWorkflow();

        var request = new SaveWorkflowRequest(
                List.of(new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1"))),
                List.of(new SaveEdgeRequest(null, "self", "n1", "n1", null, null, null, false, null, null))
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEdgeReferencingNonExistentNode() throws Exception {
        String wfId = createWorkflow();

        var request = new SaveWorkflowRequest(
                List.of(new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1"))),
                List.of(new SaveEdgeRequest(null, "bad", "n1", "n999", null, null, null, false, null, null))
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEdgesInGetWorkflow() throws Exception {
        String wfId = createWorkflow();

        var saveRequest = new SaveWorkflowRequest(
                List.of(
                        new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1")),
                        new SaveWorkflowRequest.SaveNodeRequest("n2", "FILTER", Map.of()),
                        new SaveWorkflowRequest.SaveNodeRequest("n3", "STOP", Map.of())
                ),
                List.of(
                        new SaveEdgeRequest(null, "step1", "n1", "n2", null, null, null, false, null, null),
                        new SaveEdgeRequest(null, "step2", "n2", "n3", null, null, null, false, null, null)
                )
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saveRequest)));

        mockMvc.perform(get("/api/workflows/" + wfId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(2))
                .andExpect(jsonPath("$.edges[0].sourceNodeId").exists())
                .andExpect(jsonPath("$.edges[0].targetNodeId").exists());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.workflow.segment.service.WorkflowServiceEdgeValidationTest" -x compileJava`
Expected: FAIL — compilation errors (service not updated yet)

- [ ] **Step 3: Rewrite `WorkflowService`**

Replace the entire `WorkflowService.java`:

```java
package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.*;
import com.workflow.segment.repository.SegmentWorkflowEdgeRepository;
import com.workflow.segment.repository.SegmentWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
        // Map client-provided string IDs to the persisted UUIDs
        Map<String, UUID> clientIdToUuid = new HashMap<>();

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
            }

            node.setType(NodeType.valueOf(nodeReq.type()));
            node.setConfig(nodeReq.config());

            // For new nodes without a UUID, we need to flush to get the generated ID
            if (node.getId() == null) {
                workflowRepository.saveAndFlush(wf);
            }

            clientIdToUuid.put(nodeReq.id() != null ? nodeReq.id() : node.getId().toString(),
                    node.getId());
        }

        // Delete nodes not in incoming set
        List<SegmentWorkflowNode> nodesToRemove = existingNodes.stream()
                .filter(n -> !incomingNodeIds.contains(n.getId()))
                .toList();
        wf.getNodes().removeAll(nodesToRemove);

        workflowRepository.saveAndFlush(wf);

        // Build node lookup for validation
        Map<UUID, SegmentWorkflowNode> nodeMap = new HashMap<>();
        for (SegmentWorkflowNode n : wf.getNodes()) {
            nodeMap.put(n.getId(), n);
            // Also map by string ID from the request
            clientIdToUuid.putIfAbsent(n.getId().toString(), n.getId());
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
```

- [ ] **Step 4: Create `InvalidEdgeException`**

Create `src/main/java/com/workflow/segment/service/InvalidEdgeException.java`:

```java
package com.workflow.segment.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidEdgeException extends RuntimeException {
    public InvalidEdgeException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: May still fail in `NodeService` and `ExecutionService` — fixed in next tasks.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/workflow/segment/service/WorkflowService.java \
    src/main/java/com/workflow/segment/service/InvalidEdgeException.java \
    src/test/java/com/workflow/segment/service/WorkflowServiceEdgeValidationTest.java
git commit -m "feat: edge upsert and validation in WorkflowService"
```

---

### Task 7: Update `NodeService` — Edge-Aware Delete, Remove parentNodeIds Handling

**Files:**
- Modify: `src/main/java/com/workflow/segment/service/NodeService.java`

- [ ] **Step 1: Rewrite `NodeService`**

Replace entire `NodeService.java`:

```java
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
        // Delete all edges where this node is source or target
        edgeRepository.deleteBySourceNodeIdOrTargetNodeId(nodeId, nodeId);
        nodeRepository.deleteById(nodeId);
    }

    private NodeResponse toResponse(SegmentWorkflowNode node) {
        return new NodeResponse(node.getId(), node.getType(), node.getConfig());
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: May still fail in `ExecutionService` — fixed next.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/workflow/segment/service/NodeService.java
git commit -m "feat: edge-aware node delete, remove parentNodeIds from NodeService"
```

---

### Task 8: Update `ExecutionService` — Build GraphEdges for Temporal

**Files:**
- Modify: `src/main/java/com/workflow/segment/service/ExecutionService.java`

- [ ] **Step 1: Update `ExecutionService`**

The key changes are in `executeWorkflow()` (build `GraphEdge` list) and `previewNode()` (find source table via edges instead of parentNodeIds).

In `executeWorkflow()` method (around line 88-124), replace the graph-building section:

Replace lines 99-110:
```java
// OLD:
List<SegmentWorkflowNode> nodes = nodeRepository.findByWorkflowId(workflowId);
List<GraphNode> graphNodes = nodes.stream().map(n -> new GraphNode(
        n.getId().toString(), n.getType().name(),
        n.getParentNodeIds() != null
                ? n.getParentNodeIds().stream().map(UUID::toString).toList()
                : List.of(),
        n.getConfig()
)).toList();

FullExecutionInput input = new FullExecutionInput(
        workflowId.toString(), execution.getId().toString(), graphNodes);
```

With:
```java
// NEW:
List<SegmentWorkflowNode> nodes = nodeRepository.findByWorkflowId(workflowId);
List<GraphNode> graphNodes = nodes.stream().map(n -> new GraphNode(
        n.getId().toString(), n.getType().name(), n.getConfig()
)).toList();

List<SegmentWorkflowEdge> edges = edgeRepository.findByWorkflowId(workflowId);
List<GraphEdge> graphEdges = edges.stream().map(e -> new GraphEdge(
        e.getId().toString(),
        e.getSourceNode().getId().toString(),
        e.getTargetNode().getId().toString(),
        e.getName(),
        e.getCondition(),
        e.isDefault(),
        e.getSortOrder() != null ? e.getSortOrder() : 0
)).toList();

FullExecutionInput input = new FullExecutionInput(
        workflowId.toString(), execution.getId().toString(), graphNodes, graphEdges);
```

Add the `edgeRepository` field and inject it. Add imports for `SegmentWorkflowEdge`, `GraphEdge`, `SegmentWorkflowEdgeRepository`.

In `previewNode()` method (around line 168-216), replace the source table resolution (lines 184-193):

Replace:
```java
// OLD:
String sourceTable = null;
if (node.getParentNodeIds() != null && !node.getParentNodeIds().isEmpty()) {
    UUID parentNodeId = node.getParentNodeIds().get(0);
    Optional<NodeExecutionResult> parentResult = nodeExecutionResultRepository
            .findTopByNodeIdAndStatusOrderByStartedAtDesc(parentNodeId, ExecutionStatus.SUCCESS);
    if (parentResult.isPresent()) {
        sourceTable = parentResult.get().getResultTableName();
    }
}
```

With:
```java
// NEW:
String sourceTable = null;
List<SegmentWorkflowEdge> incomingEdges = edgeRepository
        .findBySourceNodeIdOrTargetNodeId(nodeId, nodeId).stream()
        .filter(e -> e.getTargetNode().getId().equals(nodeId))
        .toList();
if (!incomingEdges.isEmpty()) {
    UUID parentNodeId = incomingEdges.get(0).getSourceNode().getId();
    Optional<NodeExecutionResult> parentResult = nodeExecutionResultRepository
            .findTopByNodeIdAndStatusOrderByStartedAtDesc(parentNodeId, ExecutionStatus.SUCCESS);
    if (parentResult.isPresent()) {
        sourceTable = parentResult.get().getResultTableName();
    }
}
```

Add the `SegmentWorkflowEdgeRepository` to the constructor:

```java
private final SegmentWorkflowEdgeRepository edgeRepository;

public ExecutionService(WorkflowExecutionRepository executionRepository,
                        SegmentWorkflowRepository workflowRepository,
                        SegmentWorkflowNodeRepository nodeRepository,
                        NodeExecutionResultRepository nodeExecutionResultRepository,
                        SegmentWorkflowEdgeRepository edgeRepository,
                        JdbcTemplate jdbcTemplate) {
    this.executionRepository = executionRepository;
    this.workflowRepository = workflowRepository;
    this.nodeRepository = nodeRepository;
    this.nodeExecutionResultRepository = nodeExecutionResultRepository;
    this.edgeRepository = edgeRepository;
    this.jdbcTemplate = jdbcTemplate;
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: May still fail in `FullExecutionWorkflowImpl` — fixed next.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/workflow/segment/service/ExecutionService.java
git commit -m "feat: build GraphEdge list in ExecutionService, edge-aware preview"
```

---

### Task 9: Rewrite `FullExecutionWorkflowImpl` — Edge-Based DAG Traversal

**Files:**
- Modify: `src/main/java/com/workflow/segment/temporal/workflows/FullExecutionWorkflowImpl.java`

- [ ] **Step 1: Rewrite the workflow implementation**

Replace entire `FullExecutionWorkflowImpl.java`:

```java
package com.workflow.segment.temporal.workflows;

import com.workflow.segment.temporal.activities.SegmentActivities;
import com.workflow.segment.temporal.model.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class FullExecutionWorkflowImpl implements FullExecutionWorkflow {

    private final SegmentActivities activities = Workflow.newActivityStub(
            SegmentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    @Override
    public FullExecutionResult execute(FullExecutionInput input) {
        List<GraphNode> graph = input.getGraph();
        List<GraphEdge> edges = input.getEdges() != null ? input.getEdges() : List.of();

        String wfId = input.getWorkflowId().replace("-", "").substring(0, 8);
        String execId = input.getExecutionId().replace("-", "").substring(0, 8);

        // Build lookup maps
        Map<String, GraphNode> nodeMap = graph.stream()
                .collect(Collectors.toMap(GraphNode::getNodeId, n -> n));

        // Build edge maps
        Map<String, List<GraphEdge>> outgoingEdges = new HashMap<>();
        Map<String, List<GraphEdge>> incomingEdges = new HashMap<>();
        for (GraphEdge edge : edges) {
            outgoingEdges.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTargetNodeId(), k -> new ArrayList<>()).add(edge);
        }
        // Sort outgoing edges by sortOrder for deterministic SPLIT evaluation
        outgoingEdges.values().forEach(list -> list.sort(Comparator.comparingInt(GraphEdge::getSortOrder)));

        // Track results and promises
        Map<String, Promise<NodeResult>> promiseMap = new HashMap<>();
        List<NodeResult> allResults = Collections.synchronizedList(new ArrayList<>());

        // Find root nodes (no incoming edges)
        List<GraphNode> roots = graph.stream()
                .filter(n -> !incomingEdges.containsKey(n.getNodeId()))
                .toList();

        // Execute each root and its subtree
        for (GraphNode root : roots) {
            executeNode(root, nodeMap, outgoingEdges, incomingEdges, promiseMap, allResults, wfId, execId);
        }

        // Wait for all promises to complete
        List<Promise<NodeResult>> allPromises = new ArrayList<>(promiseMap.values());
        if (!allPromises.isEmpty()) {
            Promise.allOf(allPromises).get();
        }

        boolean anyFailed = allResults.stream().anyMatch(r -> "FAILED".equals(r.getStatus()));
        String finalStatus = anyFailed ? "FAILED" : "SUCCESS";

        activities.completeExecution(input.getExecutionId(), allResults, finalStatus);

        return new FullExecutionResult(finalStatus, allResults);
    }

    private void executeNode(GraphNode node, Map<String, GraphNode> nodeMap,
                             Map<String, List<GraphEdge>> outgoingEdges,
                             Map<String, List<GraphEdge>> incomingEdges,
                             Map<String, Promise<NodeResult>> promiseMap,
                             List<NodeResult> allResults,
                             String wfId, String execId) {
        if (promiseMap.containsKey(node.getNodeId())) return;

        String nodeType = node.getType();

        if ("SPLIT".equals(nodeType) || "JOIN".equals(nodeType)) {
            Promise<NodeResult> structuralPromise;

            if ("JOIN".equals(nodeType)) {
                List<GraphEdge> incoming = incomingEdges.getOrDefault(node.getNodeId(), List.of());
                List<Promise<NodeResult>> parentPromises = incoming.stream()
                        .map(edge -> {
                            if (!promiseMap.containsKey(edge.getSourceNodeId())) {
                                executeNode(nodeMap.get(edge.getSourceNodeId()), nodeMap,
                                        outgoingEdges, incomingEdges, promiseMap, allResults, wfId, execId);
                            }
                            return promiseMap.get(edge.getSourceNodeId());
                        })
                        .toList();

                Promise<NodeResult> firstParentPromise = parentPromises.get(0);
                structuralPromise = Promise.allOf(parentPromises).thenApply(v -> {
                    String resultTable = firstParentPromise.get().getResultTable();
                    NodeResult nr = new NodeResult(node.getNodeId(), nodeType, resultTable,
                            0, 0, 0, "SUCCESS", null, null);
                    allResults.add(nr);
                    return nr;
                });
            } else {
                // SPLIT: passthrough — condition evaluation happens when scheduling children
                List<GraphEdge> incoming = incomingEdges.getOrDefault(node.getNodeId(), List.of());
                if (!incoming.isEmpty()) {
                    String parentId = incoming.get(0).getSourceNodeId();
                    if (!promiseMap.containsKey(parentId)) {
                        executeNode(nodeMap.get(parentId), nodeMap, outgoingEdges, incomingEdges,
                                promiseMap, allResults, wfId, execId);
                    }
                    structuralPromise = promiseMap.get(parentId).thenApply(parentResult -> {
                        NodeResult nr = new NodeResult(node.getNodeId(), nodeType,
                                parentResult.getResultTable(), 0, 0, 0, "SUCCESS", null, null);
                        allResults.add(nr);
                        return nr;
                    });
                } else {
                    structuralPromise = Async.function(() -> {
                        NodeResult nr = new NodeResult(node.getNodeId(), nodeType, null,
                                0, 0, 0, "SUCCESS", null, null);
                        allResults.add(nr);
                        return nr;
                    });
                }
            }

            promiseMap.put(node.getNodeId(), structuralPromise);

            // Schedule children via outgoing edges
            List<GraphEdge> outgoing = outgoingEdges.getOrDefault(node.getNodeId(), List.of());
            for (GraphEdge edge : outgoing) {
                GraphNode child = nodeMap.get(edge.getTargetNodeId());
                if (child != null) {
                    executeNode(child, nodeMap, outgoingEdges, incomingEdges,
                            promiseMap, allResults, wfId, execId);
                }
            }
            return;
        }

        // Data nodes: wait for parent via incoming edge, then execute
        Promise<NodeResult> nodePromise;
        List<GraphEdge> incoming = incomingEdges.getOrDefault(node.getNodeId(), List.of());

        if (!incoming.isEmpty()) {
            String parentId = incoming.get(0).getSourceNodeId();
            if (!promiseMap.containsKey(parentId)) {
                executeNode(nodeMap.get(parentId), nodeMap, outgoingEdges, incomingEdges,
                        promiseMap, allResults, wfId, execId);
            }
            nodePromise = promiseMap.get(parentId).thenApply(parentResult ->
                    executeDataNode(node, parentResult.getResultTable(), wfId, execId, allResults));
        } else {
            nodePromise = Async.function(() -> executeDataNode(node, null, wfId, execId, allResults));
        }

        promiseMap.put(node.getNodeId(), nodePromise);

        // Schedule children via outgoing edges
        List<GraphEdge> outgoing = outgoingEdges.getOrDefault(node.getNodeId(), List.of());
        for (GraphEdge edge : outgoing) {
            GraphNode child = nodeMap.get(edge.getTargetNodeId());
            if (child != null) {
                executeNode(child, nodeMap, outgoingEdges, incomingEdges,
                        promiseMap, allResults, wfId, execId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private NodeResult executeDataNode(GraphNode node, String sourceTable,
                                       String wfId, String execId, List<NodeResult> allResults) {
        String nodeShort = node.getNodeId().replace("-", "").substring(0, 8);
        String targetTable = "wf_" + wfId + "_e_" + execId + "_n_" + nodeShort + "_r";
        NodeResult result;

        try {
            result = switch (node.getType()) {
                case "START_FILE_UPLOAD" -> {
                    String filePath = (String) node.getConfig().get("file_path");
                    var r = activities.fileUploadActivity(new FileUploadInput(filePath, targetTable, null));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            0, r.getRowCount(), 0, "SUCCESS", null, null);
                }
                case "START_QUERY" -> {
                    String rawSql = (String) node.getConfig().get("raw_sql");
                    var r = activities.startQueryActivity(new StartQueryInput(rawSql, targetTable));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            0, r.getRowCount(), 0, "SUCCESS", null, null);
                }
                case "FILTER" -> {
                    var config = node.getConfig();
                    var r = activities.filterActivity(new FilterInput(
                            sourceTable, targetTable,
                            (String) config.get("data_mart_table"), (String) config.get("join_key"),
                            (String) config.get("mode"), (Map<String, Object>) config.get("conditions"),
                            !Boolean.FALSE.equals(config.get("distinct"))));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            r.getInputCount(), r.getOutputCount(), r.getFilteredCount(), "SUCCESS", null, null);
                }
                case "ENRICH" -> {
                    var config = node.getConfig();
                    var r = activities.enrichActivity(new EnrichInput(
                            sourceTable, targetTable,
                            (String) config.get("data_mart_table"), (String) config.get("mode"),
                            (String) config.get("join_key"),
                            config.get("select_columns") != null ? (List<String>) config.get("select_columns") : null));
                    yield new NodeResult(node.getNodeId(), node.getType(), r.getResultTable(),
                            r.getInputCount(), r.getOutputCount(), 0, "SUCCESS", null, null);
                }
                case "STOP" -> {
                    Object rawPath = node.getConfig() != null ? node.getConfig().get("output_file_path") : null;
                    String outputPath = (rawPath instanceof String s && !s.isBlank()) ? s
                            : "./outputs/wf_" + wfId + "/exec_" + execId + "/stop_" + nodeShort + ".csv";
                    var r = activities.stopNodeActivity(new StopNodeInput(sourceTable, outputPath));
                    yield new NodeResult(node.getNodeId(), node.getType(), null,
                            r.getRowCount(), r.getRowCount(), 0, "SUCCESS", null, r.getFilePath());
                }
                default -> throw new RuntimeException("Unknown node type: " + node.getType());
            };
        } catch (Exception e) {
            result = new NodeResult(node.getNodeId(), node.getType(), null,
                    0, 0, 0, "FAILED", e.getMessage(), null);
        }

        allResults.add(result);
        return result;
    }
}
```

- [ ] **Step 2: Verify full project compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/workflow/segment/temporal/workflows/FullExecutionWorkflowImpl.java
git commit -m "feat: edge-based DAG traversal in FullExecutionWorkflowImpl"
```

---

### Task 10: Update Existing Tests

**Files:**
- Modify: `src/test/java/com/workflow/segment/controller/WorkflowControllerTest.java`
- Modify: `src/test/java/com/workflow/segment/controller/NodeControllerTest.java`

- [ ] **Step 1: Read current `NodeControllerTest`**

Read the file to understand what needs updating.

- [ ] **Step 2: Update `WorkflowControllerTest`**

The `shouldGetWorkflowById` test checks `jsonPath("$.nodes")` — this should still work. But the response now includes `$.edges`, so optionally add a check:

In `shouldGetWorkflowById`, add after the existing assertion:
```java
.andExpect(jsonPath("$.edges").isArray());
```

- [ ] **Step 3: Update `NodeControllerTest`**

Remove any references to `parentNodeIds` and `position` in the test's request bodies. For example, if the test creates nodes with `AddNodeRequest`, update to use the new constructor without `parentNodeIds` and `position`.

Replace `AddNodeRequest` usages from:
```java
new AddNodeRequest(List.of(), "START_QUERY", Map.of("raw_sql", "SELECT 1"), 0)
```
To:
```java
new AddNodeRequest("START_QUERY", Map.of("raw_sql", "SELECT 1"))
```

Replace `UpdateNodeRequest` usages from:
```java
new UpdateNodeRequest(List.of("..."), Map.of(...))
```
To:
```java
new UpdateNodeRequest(Map.of(...))
```

Remove assertions checking `parentNodeIds` or `position` in response JSON.

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/workflow/segment/controller/WorkflowControllerTest.java \
    src/test/java/com/workflow/segment/controller/NodeControllerTest.java
git commit -m "test: update existing tests for edge model changes"
```

---

### Task 11: Run Edge Validation Tests and Fix Issues

**Files:**
- Test: `src/test/java/com/workflow/segment/service/WorkflowServiceEdgeValidationTest.java`

- [ ] **Step 1: Run the edge validation tests**

Run: `./gradlew test --tests "com.workflow.segment.service.WorkflowServiceEdgeValidationTest"`
Expected: All 4 tests PASS

- [ ] **Step 2: If any test fails, debug and fix**

Common issues:
- H2 compatibility with JSON columns — may need `@Column(columnDefinition = "text")` (already included)
- `saveAndFlush` timing — ensure nodes are persisted before edges reference them
- `tryParseUuid` returning null for client-generated non-UUID IDs — the mapping logic handles this

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: ALL tests PASS

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve edge validation test issues"
```

---

### Task 12: Full Build Verification

- [ ] **Step 1: Clean build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all tests pass**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Start the application (smoke test)**

Run: `./gradlew bootRun` (stop after startup)
Expected: Application starts without errors, Hibernate auto-creates `segment_workflow_edge` table

- [ ] **Step 4: Final commit if any remaining changes**

```bash
git status
# If clean, no commit needed
```
