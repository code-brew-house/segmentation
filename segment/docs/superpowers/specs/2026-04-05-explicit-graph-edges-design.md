# Explicit Graph Edges Design

**Date:** 2026-04-05
**Status:** Approved
**Author:** Tushar + Claude

## Problem

The current graph model uses implicit edges — `parentNodeIds` stored as a UUID list on each node. This has three limitations:

1. **No edge metadata** — cannot store names, labels, conditions, or display properties on edges
2. **Frontend mismatch** — React Flow models edges as first-class objects with `source`, `target`, and handles; the backend has no equivalent
3. **Messy traversal** — Temporal workflow execution rebuilds topology from parent lists instead of explicit edge objects

## Decision

**Approach A: Full Edge Entity.** Introduce `SegmentWorkflowEdge` as a JPA entity and `GraphEdge` as a Temporal runtime model. Remove `parentNodeIds` from nodes entirely. Edges become the single source of truth for graph topology.

## Requirements

- Edges carry: name/label, condition (for SPLIT routing), display metadata, React Flow handles
- SPLIT nodes use mutually exclusive conditions with one default/else edge, evaluated in `sortOrder`
- Edge execution metrics are derived at API level from adjacent node execution results (no separate `EdgeExecutionResult` entity)
- Frontend sends `nodes[]` + `edges[]` together via the existing `PUT /api/workflows/{id}` endpoint

---

## Section 1: Domain Models

### `SegmentWorkflowEdge` (JPA Entity — new)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `UUID` (generated) | Primary key |
| `workflow` | `SegmentWorkflow` (ManyToOne, lazy) | FK to workflow |
| `name` | `String` (nullable) | Display label, e.g. "High-value customers" |
| `sourceNode` | `SegmentWorkflowNode` (ManyToOne, lazy) | FK — the node this edge leaves |
| `targetNode` | `SegmentWorkflowNode` (ManyToOne, lazy) | FK — the node this edge enters |
| `sourceHandle` | `String` (nullable) | React Flow handle ID on source node |
| `targetHandle` | `String` (nullable) | React Flow handle ID on target node |
| `condition` | `Map<String, Object>` (JSON, nullable) | Filter expression for SPLIT edges |
| `isDefault` | `boolean` | `true` = the else/fallback edge on a SPLIT |
| `sortOrder` | `Integer` | Evaluation priority for SPLIT conditions |
| `metadata` | `Map<String, Object>` (JSON, nullable) | Arbitrary display metadata (color, style, etc.) |

**Handle explanation:** React Flow nodes can have multiple connection points (handles). `sourceHandle` identifies which output port on the source node the edge leaves from (e.g., `"a"`, `"b"`, `"default"` on a SPLIT). `targetHandle` identifies which input port on the target node. Most nodes have a single input/output, so handles are often `null`.

### `SegmentWorkflowNode` changes

- **Remove:** `parentNodeIds` field and the `@ElementCollection` / `node_parent_ids` collection table
- **Remove:** `position` field — execution order is derived via topological sort on edges; visual canvas coordinates are managed client-side by React Flow
- Node becomes a pure node — topology is fully owned by edges

### `SegmentWorkflow` changes

- **Add:** `@OneToMany(mappedBy = "workflow", cascade = ALL, orphanRemoval = true) List<SegmentWorkflowEdge> edges`

### `GraphEdge` (Temporal runtime model — new)

| Field | Type | Notes |
|-------|------|-------|
| `edgeId` | `String` | UUID as string |
| `sourceNodeId` | `String` | Source node ID |
| `targetNodeId` | `String` | Target node ID |
| `name` | `String` | Display label |
| `condition` | `Map<String, Object>` | SPLIT filter expression |
| `isDefault` | `boolean` | Else edge flag |
| `sortOrder` | `int` | Evaluation order |

### `GraphNode` changes

- **Remove:** `parentIds` — no longer needed, edges carry topology

---

## Section 2: DTOs & API Layer

### New Edge DTOs

**`EdgeResponse`** (returned from API)

| Field | Type |
|-------|------|
| `id` | `UUID` |
| `name` | `String` |
| `sourceNodeId` | `UUID` |
| `targetNodeId` | `UUID` |
| `sourceHandle` | `String` |
| `targetHandle` | `String` |
| `condition` | `Map<String, Object>` |
| `isDefault` | `boolean` |
| `sortOrder` | `Integer` |
| `metadata` | `Map<String, Object>` |

**`SaveEdgeRequest`** (used in bulk workflow save)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | Client-generated ID (React Flow edge ID), nullable for new edges |
| `name` | `String` | |
| `source` | `String` | Source node ID (matches React Flow field name) |
| `target` | `String` | Target node ID (matches React Flow field name) |
| `sourceHandle` | `String` | |
| `targetHandle` | `String` | |
| `condition` | `Map<String, Object>` | |
| `isDefault` | `boolean` | |
| `sortOrder` | `Integer` | |
| `metadata` | `Map<String, Object>` | |

### Modified DTOs

- **`SaveWorkflowRequest`** — add `edges: List<SaveEdgeRequest>`
- **`WorkflowDetailResponse`** — add `edges: List<EdgeResponse>`
- **`SaveNodeRequest`** — remove `parentNodeIds` and `position`
- **`NodeResponse`** — remove `parentNodeIds` and `position`
- **`AddNodeRequest` / `UpdateNodeRequest`** — remove `parentNodeIds`

### API Endpoints

No separate edge controller. Edges managed as part of the workflow graph:

- **`PUT /api/workflows/{id}`** — bulk upsert of nodes + edges in single transaction
- **`GET /api/workflows/{id}`** — response includes `edges` list alongside `nodes`
- **`DELETE /api/workflows/{workflowId}/nodes/{nodeId}`** — cascade deletes edges where node is source or target (no downstream node cascade — frontend decides)

---

## Section 3: Service Layer

### `WorkflowService` changes

**`saveWorkflow()`** extended to:

1. Upsert nodes (existing logic)
2. Upsert edges — match by `id`, create new, update existing, delete removed
3. **Validation before save:**
   - Every edge's `source` and `target` must reference a node in the same workflow
   - SPLIT nodes: exactly one outgoing edge must have `isDefault = true`
   - SPLIT nodes: non-default outgoing edges must have a non-null `condition`
   - No duplicate edges (same source + target + sourceHandle combination)
   - No self-referencing edges (source != target)
4. Single transaction — nodes and edges committed together

**`getWorkflow()`** — eagerly fetch edges alongside nodes.

### `NodeService` changes

**`deleteNode()`** — new logic:
1. Find all edges where node is source or target
2. Delete those edges
3. Delete the node
4. No cascade to downstream nodes — frontend manages orphan cleanup

**`addNode()` / `updateNode()`** — remove `parentNodeIds` handling.

### `ExecutionService` changes

Convert `SegmentWorkflowNode` + `SegmentWorkflowEdge` → `GraphNode` + `GraphEdge`. Package both into `FullExecutionInput`.

### No separate `EdgeService`

Edge validation and conversion lives in `WorkflowService.saveWorkflow()`.

---

## Section 4: Temporal Workflow Execution

### `FullExecutionInput` changes

- **Add:** `edges: List<GraphEdge>` alongside existing `graph: List<GraphNode>`

### `FullExecutionWorkflowImpl` — graph traversal rewrite

**Build maps from edges:**
- `outgoingEdges`: `sourceNodeId -> List<GraphEdge>` (sorted by `sortOrder`)
- `incomingEdges`: `targetNodeId -> List<GraphEdge>`
- `nodeMap`: `nodeId -> GraphNode`

**Find root nodes:** nodes with no entries in `incomingEdges`.

**Execute DAG** (same async Promise pattern, edge-aware):
```
executeNode(node, maps, promises, results):
    result = executeActivity(node)

    for edge in outgoingEdges[node.id]:
        childNode = nodeMap[edge.targetNodeId]
        scheduleChild(childNode, edge, result)
```

**SPLIT node — condition evaluation on edges:**
```
executeSplitNode(node, outgoingEdges, parentResult):
    sourceTable = parentResult.resultTable

    // evaluate non-default edges in sortOrder
    for edge in outgoingEdges (sorted, non-default first):
        filteredTable = applyCondition(sourceTable, edge.condition)
        scheduleChild(edge.targetNodeId, filteredTable)

    // default edge gets remaining rows
    defaultEdge = find where isDefault == true
    remainingTable = applyDefaultFilter(sourceTable, allConditions)
    scheduleChild(defaultEdge.targetNodeId, remainingTable)
```

**JOIN node** — waits for all incoming edges:
```
executeJoinNode(node, incomingEdges, promises):
    parentPromises = incomingEdges[node.id]
        .map(edge -> promises[edge.sourceNodeId])
    Promise.allOf(parentPromises).get()
    // merge parent result tables
```

### Activity changes

**New activity:** `applySplitCondition(sourceTable, condition, targetTable)` — creates a filtered table based on the edge's condition expression. Replaces current SPLIT pass-through behavior.

**Existing activities** (FileUpload, Query, Filter, Enrich, Stop) — unchanged.

### Edge execution metrics

Derived at API level: source node's `outputCount` and target node's `inputCount`. No `EdgeExecutionResult` entity.

---

## Migration Strategy

1. **Database:** Add `segment_workflow_edge` table. Drop `node_parent_ids` table. Remove `parent_node_ids` references from node entity.
2. **Data migration:** For existing workflows, generate edges from current `parentNodeIds` — one edge per parent-child relationship, with default names and no conditions.
3. **Frontend:** Update React Flow state to send edges in `SaveWorkflowRequest` and consume edges from `WorkflowDetailResponse`.
4. **Temporal:** Deploy updated workflow code. Existing running workflows complete with old logic; new executions use edge-based traversal.
