# Segment Workflow System — Architecture & API Specification

**Version:** 1.0
**Date:** 2026-04-05
**Stack:** Spring Boot 3.5.x, Java 21, PostgreSQL, Temporal, React Flow (frontend)

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Domain Model](#3-domain-model)
4. [Node Types](#4-node-types)
5. [Workflow as a Graph — Nodes & Edges](#5-workflow-as-a-graph--nodes--edges)
6. [Workflow Execution Deep Dive — Engine, State & Storage](#6-workflow-execution-deep-dive--engine-state--storage)
7. [REST API Specification](#7-rest-api-specification)
8. [Temporal Workflow Engine](#8-temporal-workflow-engine)
9. [SQL Generation](#9-sql-generation)
10. [Execution Lifecycle](#10-execution-lifecycle)
11. [End-to-End Workflow Flow](#11-end-to-end-workflow-flow)
12. [Data Marts](#12-data-marts)
13. [Error Handling](#13-error-handling)
14. [Configuration](#14-configuration)
15. [Planned Enhancements](#15-planned-enhancements)

---

## 1. System Overview

The Segment Workflow System is a DAG-based data processing engine that allows users to build, configure, and execute multi-step data pipelines through a visual canvas. Users compose workflows from typed nodes — ingesting data via CSV upload or SQL query, transforming it through filters and enrichments, branching and merging paths, and exporting final results.

**Key capabilities:**

- Visual DAG builder backed by a REST API
- Seven core node types covering ingest, transform, branch, merge, and export
- Asynchronous workflow execution powered by Temporal
- Per-node result tables in PostgreSQL for inspection and debugging
- SQL preview without execution for validation
- Execution history with per-node metrics

---

## 2. Architecture

```
┌──────────────┐       REST API       ┌──────────────────┐
│  React Flow  │ ◄──────────────────► │  Spring Boot     │
│  Frontend    │                      │  Controllers     │
└──────────────┘                      └────────┬─────────┘
                                               │
                                    ┌──────────┴─────────┐
                                    │  Service Layer      │
                                    │  (WorkflowService,  │
                                    │   ExecutionService,  │
                                    │   NodeService,       │
                                    │   SqlPreviewService) │
                                    └──────────┬─────────┘
                                               │
                              ┌────────────────┼────────────────┐
                              │                │                │
                     ┌────────▼──────┐  ┌──────▼──────┐  ┌─────▼──────┐
                     │  JPA / Repos  │  │  Temporal    │  │  JDBC      │
                     │  (Workflow,   │  │  Client      │  │  (Dynamic  │
                     │   Node, Edge, │  │  (async      │  │   SQL,     │
                     │   Execution)  │  │   dispatch)  │  │   temp     │
                     └───────┬───────┘  └──────┬───────┘  │   tables)  │
                             │                 │          └─────┬──────┘
                             │          ┌──────▼──────┐         │
                             │          │  Temporal    │         │
                             │          │  Worker      │         │
                             │          │  (Activities)├─────────┘
                             │          └─────────────┘
                             │
                      ┌──────▼──────┐
                      │ PostgreSQL  │
                      │ (entities + │
                      │  temp tables│
                      │  + data     │
                      │    marts)   │
                      └─────────────┘
```

### Component Summary

| Component | Responsibility |
|-----------|---------------|
| **Controllers** | REST endpoints for CRUD, execution, preview |
| **Services** | Business logic, validation, Temporal dispatch |
| **Repositories** | JPA data access for all entities |
| **Temporal Workflows** | DAG traversal, async node execution orchestration |
| **Temporal Activities** | Individual node operations (SQL, CSV, file I/O) |
| **PostgreSQL** | Persistent entities, data marts, temporary result tables |

---

## 3. Domain Model

### 3.1 Entity Relationship Diagram

```
SegmentWorkflow
├── id: UUID (PK)
├── name: String
├── createdBy: String
├── createdAt: Instant (auto)
├── status: WorkflowStatus
│
├──► nodes: List<SegmentWorkflowNode>  (1:N, cascade ALL)
├──► edges: List<SegmentWorkflowEdge>  (1:N, cascade ALL) [planned]
└──► executions: List<WorkflowExecution> (1:N)

SegmentWorkflowNode
├── id: UUID (PK)
├── workflow: SegmentWorkflow (FK)
├── type: NodeType
├── parentNodeIds: List<UUID>  [to be removed — see §15]
├── config: Map<String, Object> (JSON)
└── position: Integer  [to be removed — see §15]

SegmentWorkflowEdge  [planned — see §15]
├── id: UUID (PK)
├── workflow: SegmentWorkflow (FK)
├── name: String
├── sourceNode: SegmentWorkflowNode (FK)
├── targetNode: SegmentWorkflowNode (FK)
├── sourceHandle: String
├── targetHandle: String
├── condition: Map<String, Object> (JSON)
├── isDefault: boolean
├── sortOrder: Integer
└── metadata: Map<String, Object> (JSON)

WorkflowExecution
├── id: UUID (PK)
├── workflow: SegmentWorkflow (FK)
├── status: ExecutionStatus
├── startedAt: Instant
├── completedAt: Instant
└──► nodeResults: List<NodeExecutionResult> (1:N, cascade ALL)

NodeExecutionResult
├── id: UUID (PK)
├── execution: WorkflowExecution (FK)
├── nodeId: UUID
├── nodeType: NodeType
├── inputRecordCount: Integer
├── filteredRecordCount: Integer
├── outputRecordCount: Integer
├── resultTableName: String
├── outputFilePath: String
├── status: ExecutionStatus
├── errorMessage: String
├── startedAt: Instant
└── completedAt: Instant

DataMart
├── id: UUID (PK)
├── tableName: String (unique)
├── schemaName: String
├── description: String
└──► columns: List<DataMartColumn> (1:N, cascade ALL)

DataMartColumn
├── id: UUID (PK)
├── dataMart: DataMart (FK)
├── columnName: String
├── dataType: String
├── description: String
└── ordinalPosition: Integer
```

### 3.2 Enums

#### WorkflowStatus

| Value | Description |
|-------|-------------|
| `DRAFT` | Being edited, not yet executed |
| `RUNNING` | Execution in progress |
| `COMPLETED` | All nodes executed successfully |
| `FAILED` | One or more nodes failed |

#### ExecutionStatus

| Value | Description |
|-------|-------------|
| `PENDING` | Waiting to start |
| `RUNNING` | Currently executing |
| `SUCCESS` | Completed successfully |
| `FAILED` | Execution failed |

#### NodeType

| Value | Category | Description |
|-------|----------|-------------|
| `START_FILE_UPLOAD` | Ingest | Load data from a CSV file |
| `START_QUERY` | Ingest | Execute a raw SQL query |
| `FILTER` | Transform | Filter rows using data mart joins and conditions |
| `ENRICH` | Transform | Add columns or rows from a data mart (legacy) |
| `ENRICHMENT` | Transform | Advanced enrichment with LINK, COLLECTION, EXPRESSION, ADD_RECORDS |
| `SPLIT` | Branch | Fan-out to multiple downstream paths |
| `JOIN` | Merge | Converge multiple upstream paths |
| `STOP` | Export | Export results to CSV file |

---

## 4. Node Types

### 4.1 START_FILE_UPLOAD

Ingests a CSV file into a temporary PostgreSQL table.

**Config:**

```json
{
  "file_path": "/path/to/upload.csv"
}
```

**Behavior:**
1. Reads CSV headers, normalizes to lowercase with underscores
2. Creates target table with all `TEXT` columns
3. Bulk inserts all rows via JDBC
4. Returns table name, row count, column names

---

### 4.2 START_QUERY

Executes a raw SQL query and materializes results into a temporary table.

**Config:**

```json
{
  "raw_sql": "SELECT * FROM customers WHERE region = 'US'"
}
```

**Behavior:**
- Executes `CREATE TABLE <target> AS <raw_sql>`
- Returns table name, row count, column names

---

### 4.3 FILTER

Filters the upstream working set by joining with a data mart table and applying conditions.

**Config:**

```json
{
  "data_mart_table": "dm_customers",
  "join_key": "customer_id",
  "mode": "JOIN",
  "conditions": {
    "operation": "AND",
    "conditions": [
      { "field": "segment", "operator": "=", "value": "premium" },
      { "field": "score", "operator": ">", "value": "100" }
    ]
  },
  "distinct": true
}
```

**Modes:**

| Mode | SQL Pattern |
|------|-------------|
| `JOIN` | `SELECT DISTINCT src.* FROM <source> src JOIN <dm> ON key WHERE <conditions>` |
| `SUBQUERY` | `SELECT * FROM <source> WHERE key IN (SELECT key FROM <dm> WHERE <conditions>)` |

**Condition operators:** `=`, `!=`, `<`, `>`, `<=`, `>=`, `IS NULL`, `IS NOT NULL`, `IN`, `NOT IN`, `BETWEEN`

**Nested conditions:** Supports recursive AND/OR grouping:

```json
{
  "operation": "OR",
  "conditions": [
    {
      "operation": "AND",
      "conditions": [
        { "field": "age", "operator": ">", "value": "25" },
        { "field": "region", "operator": "=", "value": "US" }
      ]
    },
    { "field": "vip", "operator": "=", "value": "true" }
  ]
}
```

**Returns:** Source table, target table, input count, filtered count, output count.

---

### 4.4 ENRICH (Legacy)

Adds columns or rows from a data mart. Supported for backward compatibility.

**Config (ADD_COLUMNS):**

```json
{
  "data_mart_table": "dm_profiles",
  "mode": "ADD_COLUMNS",
  "join_key": "customer_id",
  "select_columns": ["age", "region"]
}
```

**SQL:** `SELECT src.*, dm.age, dm.region FROM <source> src LEFT JOIN dm_profiles dm ON src.customer_id::text = dm.customer_id::text`

**Config (ADD_RECORDS):**

```json
{
  "data_mart_table": "dm_extra_contacts",
  "mode": "ADD_RECORDS"
}
```

**SQL:** `SELECT * FROM <source> UNION ALL SELECT * FROM dm_extra_contacts`

---

### 4.5 ENRICHMENT (Advanced)

Full-featured enrichment engine supporting multiple enrichment sources per node.

**Config schema:**

```json
{
  "keep_upstream_data": true,
  "enrichments": [
    { "id": "enr_1", "type": "LINK", ... },
    { "id": "enr_2", "type": "COLLECTION", ... },
    { "id": "enr_3", "type": "EXPRESSION", ... },
    { "id": "enr_4", "type": "ADD_RECORDS", ... }
  ]
}
```

#### LINK — Join columns from a related table (1:1 / 0:1)

```json
{
  "id": "enr_1",
  "type": "LINK",
  "data_mart_table": "dm_customer_profile",
  "label": "Customer Profile",
  "join_type": "LEFT",
  "join_conditions": [
    { "source_column": "customer_id", "target_column": "id", "operator": "EQUALS" }
  ],
  "select_columns": [
    { "column": "email", "alias": null },
    { "column": "loyalty_tier", "alias": "customer_tier" }
  ],
  "deduplicate": true,
  "dedup_order_by": "updated_at",
  "dedup_order_dir": "DESC",
  "filter": {
    "logic": "AND",
    "conditions": [
      { "column": "status", "operator": "EQUALS", "value": "active" }
    ]
  }
}
```

#### COLLECTION — Aggregate a 1:N related table

```json
{
  "id": "enr_2",
  "type": "COLLECTION",
  "data_mart_table": "dm_orders",
  "label": "Order Aggregates",
  "join_type": "LEFT",
  "join_conditions": [
    { "source_column": "customer_id", "target_column": "customer_id", "operator": "EQUALS" }
  ],
  "filter": {
    "logic": "AND",
    "conditions": [
      { "column": "order_date", "operator": "GREATER_THAN", "value": "2024-01-01" }
    ]
  },
  "aggregations": [
    { "function": "COUNT", "column": "*", "alias": "total_orders" },
    { "function": "SUM", "column": "amount", "alias": "total_spend" },
    { "function": "MAX", "column": "order_date", "alias": "last_order_date" }
  ],
  "limit": null
}
```

**Aggregation functions:** `COUNT`, `COUNT_DISTINCT`, `SUM`, `AVG`, `MIN`, `MAX`, `FIRST`, `LAST`

#### EXPRESSION — Computed columns (no external join)

```json
{
  "id": "enr_3",
  "type": "EXPRESSION",
  "expressions": [
    { "alias": "full_name", "sql_expression": "first_name || ' ' || last_name" },
    { "alias": "days_since_signup", "sql_expression": "CURRENT_DATE - signup_date::date" },
    { "alias": "spend_bucket", "sql_expression": "CASE WHEN total_spend > 1000 THEN 'HIGH' WHEN total_spend > 100 THEN 'MEDIUM' ELSE 'LOW' END" }
  ]
}
```

**Security:** Expressions are validated against DDL keywords (`DROP`, `DELETE`, `INSERT`, `UPDATE`, `ALTER`, `CREATE`, `TRUNCATE`), statement terminators (`;`), and SQL comments (`--`, `/*`).

#### ADD_RECORDS — Append rows (UNION ALL)

```json
{
  "id": "enr_4",
  "type": "ADD_RECORDS",
  "data_mart_table": "dm_extra_contacts"
}
```

**Constraint:** `ADD_RECORDS` must be the only enrichment when used.

#### SQL Generation Strategy

The enrichment activity builds SQL using CTEs:

```sql
CREATE TABLE <target_table> AS
WITH base AS (
  SELECT * FROM <source_table>
),
enr_1 AS (
  SELECT DISTINCT ON (id) email, loyalty_tier AS customer_tier, id
  FROM dm_customer_profile
  WHERE status = 'active'
  ORDER BY id, updated_at DESC
),
enr_2 AS (
  SELECT customer_id,
         COUNT(*) AS total_orders,
         SUM(amount) AS total_spend,
         MAX(order_date) AS last_order_date
  FROM dm_orders
  WHERE order_date > '2024-01-01'
  GROUP BY customer_id
)
SELECT base.*,
       enr_1.email, enr_1.customer_tier,
       enr_2.total_orders, enr_2.total_spend, enr_2.last_order_date,
       base.first_name || ' ' || base.last_name AS full_name,
       CASE WHEN enr_2.total_spend > 1000 THEN 'HIGH'
            WHEN enr_2.total_spend > 100 THEN 'MEDIUM'
            ELSE 'LOW' END AS spend_bucket
FROM base
LEFT JOIN enr_1 ON base.customer_id = enr_1.id
LEFT JOIN enr_2 ON base.customer_id = enr_2.customer_id
```

#### Validation Rules

| Rule | Scope | Error |
|------|-------|-------|
| `enrichments` must not be empty | Node | "At least one enrichment source is required" |
| Unique enrichment `id` | Node | "Duplicate enrichment ID: {id}" |
| LINK/COLLECTION require `data_mart_table` | Source | "Data mart table is required for {type}" |
| LINK/COLLECTION require `join_conditions` | Source | "At least one join condition is required" |
| COLLECTION requires `aggregations` | Source | "At least one aggregation is required" |
| EXPRESSION requires `expressions` | Source | "At least one expression is required" |
| Unique aggregation `alias` | Source | "Duplicate aggregation alias: {alias}" |
| FIRST/LAST require `order_by` | Aggregation | "order_by is required for {function}" |
| No DDL in `sql_expression` | Expression | "Expression contains forbidden SQL: {keyword}" |
| ADD_RECORDS must be sole enrichment | Node | "ADD_RECORDS must be the only enrichment when used" |

---

### 4.6 SPLIT

Fans out the workflow into multiple downstream branches.

**Current behavior:** Structural pass-through — passes the parent result table to all children unchanged.

**Planned behavior (with explicit edges):** Each outgoing edge carries a condition. Non-default edges are evaluated in `sortOrder`; the default edge receives remaining rows. See [Section 12](#12-planned-enhancements).

---

### 4.7 JOIN

Converges multiple upstream branches into a single path.

**Behavior:** Waits for all parent nodes to complete, then uses the first parent's result table as its output.

---

### 4.8 STOP

Exports the upstream result set to a CSV file.

**Config:**

```json
{
  "output_file_name": "export.csv"
}
```

**Behavior:**
1. Queries all rows from source table
2. Writes CSV to `./outputs/<file>`
3. Returns file path and row count

---

## 5. Workflow as a Graph — Nodes & Edges

A workflow is a **directed acyclic graph (DAG)**. Every workflow is composed of **nodes** (processing steps) connected by **edges** (data flow paths). Understanding this graph model is essential to working with the system.

### 5.1 Graph Concepts

```
  ┌────────────────────────────────────────────────────────────────────────────┐
  │                          WORKFLOW (DAG)                                    │
  │                                                                            │
  │   A "workflow" is a container that owns a set of nodes and the edges       │
  │   that connect them. It has a name, a creator, a status, and a version    │
  │   of the graph that can be saved, executed, and inspected.                │
  │                                                                            │
  │   ┌──────┐         ┌──────┐         ┌──────┐         ┌──────┐            │
  │   │ NODE │──edge──►│ NODE │──edge──►│ NODE │──edge──►│ NODE │            │
  │   │  A   │         │  B   │         │  C   │         │  D   │            │
  │   └──────┘         └──────┘         └──────┘         └──────┘            │
  │                                                                            │
  │   Node = a typed processing step (ingest, transform, export)              │
  │   Edge = a directed connection carrying data from source → target         │
  └────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 What Is a Node?

A node is a single processing step in the pipeline. Each node has:

| Property | Description |
|----------|-------------|
| **id** | Unique UUID identifier |
| **type** | One of the `NodeType` enum values (START_QUERY, FILTER, ENRICHMENT, STOP, etc.) |
| **config** | A JSON map of type-specific configuration (SQL query, join keys, conditions, etc.) |
| **parentNodeIds** | List of upstream node IDs this node receives data from (current model) |

Nodes are categorized by their role in the pipeline:

```
  ┌─────────────┐    ┌──────────────────┐    ┌─────────────┐    ┌──────────┐
  │   INGEST    │    │    TRANSFORM     │    │   CONTROL   │    │  OUTPUT  │
  │             │    │                  │    │   FLOW      │    │          │
  │ START_FILE  │    │ FILTER           │    │ SPLIT       │    │ STOP     │
  │ START_QUERY │    │ ENRICH           │    │ JOIN        │    │          │
  │             │    │ ENRICHMENT       │    │             │    │          │
  └──────┬──────┘    └───────┬──────────┘    └──────┬──────┘    └──────────┘
         │                   │                      │
         │  Produce data     │  Transform data      │  Route data
         │  from external    │  using data marts,   │  between
         │  sources          │  conditions, and     │  parallel
         │                   │  expressions         │  branches
```

### 5.3 What Is an Edge?

An edge is a directed connection from a **source node** to a **target node**. It represents data flowing downstream.

**Current model:** Edges are implicit — stored as `parentNodeIds` on the target node. If node B has `parentNodeIds: [A]`, that means there is an edge from A → B.

**Planned model (§15):** Edges become first-class `SegmentWorkflowEdge` entities with metadata:

```
  Edge
  ├── sourceNode ──► the node data flows FROM
  ├── targetNode ──► the node data flows TO
  ├── sourceHandle   (React Flow output port, e.g. "a", "b" on SPLIT)
  ├── targetHandle   (React Flow input port)
  ├── condition      (filter expression for SPLIT routing)
  ├── isDefault      (true = the else/fallback edge on SPLIT)
  ├── sortOrder      (evaluation priority for SPLIT conditions)
  ├── name           (display label)
  └── metadata       (color, style, etc.)
```

### 5.4 Graph Topology Rules

| Rule | Description |
|------|-------------|
| **Acyclic** | No cycles allowed — data flows in one direction only |
| **Rooted** | At least one root node (no parents) — typically a START node |
| **Terminated** | At least one leaf node (no children) — typically a STOP node |
| **Single output** | Most nodes produce one output table passed to all children |
| **SPLIT fan-out** | SPLIT nodes connect to multiple children (one edge per branch) |
| **JOIN fan-in** | JOIN nodes accept multiple parents (one edge per incoming branch) |
| **No orphans** | Every non-root node must be reachable from a root |

### 5.5 Graph Examples

#### Linear Pipeline

```
  START_QUERY ──► FILTER ──► ENRICHMENT ──► STOP

  Nodes: 4
  Edges: 3  (A→B, B→C, C→D)
  
  parentNodeIds:
    A: []         (root)
    B: [A]
    C: [B]
    D: [C]        (leaf)
```

#### Diamond (Split + Join)

```
                    START_QUERY
                         │
                       SPLIT
                      ╱      ╲
               FILTER(US)   FILTER(EU)
                      ╲      ╱
                       JOIN
                         │
                       STOP

  Nodes: 6
  Edges: 6  (START→SPLIT, SPLIT→FILTER_US, SPLIT→FILTER_EU,
              FILTER_US→JOIN, FILTER_EU→JOIN, JOIN→STOP)

  parentNodeIds:
    START:     []
    SPLIT:     [START]
    FILTER_US: [SPLIT]
    FILTER_EU: [SPLIT]
    JOIN:      [FILTER_US, FILTER_EU]
    STOP:      [JOIN]
```

#### Multi-Source Merge

```
  START_FILE_UPLOAD ──► FILTER ──┐
                                  ├──► JOIN ──► ENRICHMENT ──► STOP
  START_QUERY ────────────────────┘

  Nodes: 5
  Edges: 4

  Two independent data sources converge at a JOIN node.
```

#### Complex Pipeline with Parallel Branches

```
  START_QUERY
       │
     SPLIT
    ╱   │   ╲
   ▼    ▼    ▼
  F_1  F_2  F_3        ◄── Three parallel FILTER branches
   │    │    │
  E_1  E_2  E_3        ◄── Each branch enriched independently
   │    │    │
   ▼    ▼    ▼
  S_1  S_2  S_3        ◄── Three separate STOP exports

  Nodes: 10
  Edges: 9
  No JOIN needed — branches export independently.
```

### 5.6 Frontend ↔ Backend Graph Mapping

The frontend (React Flow) and backend represent the same graph differently:

```
  ┌─────────────────────────────┐     ┌─────────────────────────────┐
  │       REACT FLOW            │     │         BACKEND             │
  │                             │     │                             │
  │  nodes: [                   │     │  nodes: [                   │
  │    {id:"A", type:"query",   │     │    {id:"A", type:"START_    │
  │     position:{x:100,y:50},  │     │     QUERY",                 │
  │     data:{config:{...}}}    │     │     parentNodeIds:[],       │
  │  ]                          │     │     config:{...}}           │
  │                             │     │  ]                          │
  │  edges: [                   │     │                             │
  │    {id:"e1",                │     │  (Current) parentNodeIds    │
  │     source:"A",             │     │  on each node encodes       │
  │     target:"B",             │     │  the edges implicitly       │
  │     sourceHandle:"default", │     │                             │
  │     targetHandle:"top"}     │     │  (Planned) edges: [         │
  │  ]                          │     │    {source:"A",target:"B",  │
  │                             │     │     sourceHandle, condition} │
  │  Canvas coordinates,        │     │  ]                          │
  │  visual styling,            │     │                             │
  │  handle positions           │     │  No visual data — pure      │
  │                             │     │  topology + config           │
  └─────────────────────────────┘     └─────────────────────────────┘

  PUT /api/workflows/{id}
  ─────────────────────────────►
  Frontend serializes its node+edge state into SaveWorkflowRequest.
  Backend upserts nodes (and edges once implemented).
```

---

## 6. Workflow Execution Deep Dive — Engine, State & Storage

This section explains exactly what happens when a workflow executes, how state is tracked, where data lives at each stage, and how the system recovers from failures.

### 6.1 Execution Model Overview

```
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                       EXECUTION ARCHITECTURE                             │
  │                                                                          │
  │  ┌──────────┐    dispatch     ┌──────────────┐    SQL    ┌───────────┐  │
  │  │ Spring   │───────────────►│   Temporal    │─────────►│PostgreSQL │  │
  │  │ Boot     │    (async)     │   Worker      │          │           │  │
  │  │ Service  │                │               │          │ temp      │  │
  │  │          │◄───────────────│  Workflows +  │◄─────────│ tables    │  │
  │  │          │   complete     │  Activities   │  results │           │  │
  │  └──────────┘                └──────────────┘          └───────────┘  │
  │                                                                          │
  │  Spring Boot:  Creates WorkflowExecution, dispatches to Temporal         │
  │  Temporal:     Orchestrates DAG traversal, retries, timeouts             │
  │  PostgreSQL:   Stores execution state + per-node result tables           │
  └──────────────────────────────────────────────────────────────────────────┘
```

### 6.2 State Machine — WorkflowExecution

A `WorkflowExecution` tracks the overall status of a single run:

```
       POST /execute
            │
            ▼
     ┌──────────┐
     │ RUNNING  │ ◄── Created by ExecutionService
     └────┬─────┘     before Temporal dispatch
          │
          │  All nodes complete
          │
     ┌────┴──────────────────────────┐
     │                               │
     ▼                               ▼
┌─────────┐                    ┌──────────┐
│ SUCCESS │                    │  FAILED  │
│         │                    │          │
│ All     │                    │ One or   │
│ nodes   │                    │ more     │
│ passed  │                    │ nodes    │
└─────────┘                    │ failed   │
                               └──────────┘
```

### 6.3 State Machine — NodeExecutionResult

Each node within an execution has its own status tracking:

```
  ┌─────────┐     Activity      ┌─────────┐
  │ PENDING │────started────────►│ RUNNING │
  └─────────┘                    └────┬────┘
                                      │
                         ┌────────────┴────────────┐
                         │                         │
                         ▼                         ▼
                   ┌─────────┐               ┌──────────┐
                   │ SUCCESS │               │  FAILED  │
                   │         │               │          │
                   │ result  │               │ error    │
                   │ Table   │               │ Message  │
                   │ created │               │ stored   │
                   └─────────┘               └──────────┘
```

### 6.4 Where Data Lives at Each Stage

```
  STAGE                 WHAT IS STORED                    WHERE
  ─────────────────────────────────────────────────────────────────────
  
  1. Workflow Design     Nodes, edges, config              segment_workflow
                                                           segment_workflow_node
                                                           node_parent_ids

  2. Execution Created   Execution record (RUNNING)        workflow_execution

  3. Node Executing      Temporal activity in progress     Temporal Server
                         (state managed by Temporal)       (event history)

  4. Node Result         Temporary result table            PostgreSQL table:
                         with actual data rows             wf_<wf>_e_<ex>_n_<nd>_r

  5. Node Metrics        Row counts, status, timing        node_execution_result

  6. CSV Export          Final output file                 Local filesystem:
                         (STOP nodes only)                 ./outputs/<filename>.csv

  7. Execution Complete  Final status (SUCCESS/FAILED),    workflow_execution
                         completedAt timestamp             (updated)
```

### 6.5 Temporal Activity Execution — Per Node Type

Each node type maps to a specific Temporal activity. The activity receives input, runs SQL against PostgreSQL, and produces a result table.

```
  ┌──────────────────────────────────────────────────────────────────────────────────────┐
  │                          NODE EXECUTION PIPELINE                                     │
  │                                                                                      │
  │   For each node in topological order:                                                │
  │                                                                                      │
  │   ┌─────────────────┐    ┌──────────────────┐    ┌────────────────────┐              │
  │   │ Receive parent  │    │ Execute Temporal  │    │ Store result in   │              │
  │   │ result table    │───►│ activity (SQL)    │───►│ temp table +      │              │
  │   │ name as input   │    │                   │    │ NodeResult object │              │
  │   └─────────────────┘    └──────────────────┘    └────────────────────┘              │
  │                                                                                      │
  │   ┌───────────────────────────────────────────────────────────────────────────┐      │
  │   │ Node Type          │ Activity              │ SQL Pattern                  │      │
  │   ├────────────────────┼───────────────────────┼──────────────────────────────┤      │
  │   │ START_FILE_UPLOAD  │ fileUploadActivity     │ CREATE TABLE + JDBC INSERT   │      │
  │   │ START_QUERY        │ startQueryActivity     │ CREATE TABLE AS <raw_sql>    │      │
  │   │ FILTER             │ filterActivity         │ CREATE TABLE AS SELECT..JOIN │      │
  │   │ ENRICH             │ enrichActivity         │ CREATE TABLE AS SELECT..JOIN │      │
  │   │ ENRICHMENT         │ enrichmentActivity     │ CREATE TABLE AS WITH..CTEs   │      │
  │   │ SPLIT              │ (pass-through)         │ No SQL — reuses parent table │      │
  │   │ JOIN               │ (pass-through)         │ No SQL — reuses parent table │      │
  │   │ STOP               │ stopNodeActivity       │ SELECT * → CSV writer        │      │
  │   └───────────────────────────────────────────────────────────────────────────┘      │
  └──────────────────────────────────────────────────────────────────────────────────────┘
```

### 6.6 DAG Traversal & Parallel Execution

The Temporal workflow executes the DAG using an async promise-based pattern:

```
  Given this graph:
  
       A ──► B ──► D ──► F
       │           ▲
       └──► C ─────┘
            │
            └──► E
  
  Execution order (with parallelism):
  
  Time ──────────────────────────────────────────────────────►
  
  T0   ┌───────┐
       │  A    │  (root — no parents, starts immediately)
       └───┬───┘
           │
  T1   ┌───▼───┐  ┌───────┐
       │  B    │  │  C    │  (both depend only on A — run in PARALLEL)
       └───┬───┘  └──┬──┬─┘
           │         │  │
  T2       │      ┌──▼──┘  
           │      │  E  │     (E depends only on C — starts as soon as C finishes)
           │      └─────┘
           │         │
  T3   ┌───▼─────────▼───┐
       │  D               │  (D depends on B AND C — waits for BOTH, then starts)
       └────────┬─────────┘
                │
  T4   ┌────────▼─────────┐
       │  F               │  (depends only on D)
       └──────────────────┘
  
  Implementation:
    promiseMap[nodeId] = Async.function(() -> {
        // Wait for all parent promises
        Promise.allOf(parentPromises).get();
        // Execute this node's activity
        return executeActivity(node, parentResult);
    })
```

### 6.7 Result Table Lifecycle

Each node's output is materialized into a PostgreSQL temporary table. Here is the full lifecycle:

```
  ┌────────────────────────────────────────────────────────────────────────────┐
  │                    RESULT TABLE LIFECYCLE                                  │
  │                                                                            │
  │                                                                            │
  │  1. CREATED                                                                │
  │     Activity runs: CREATE TABLE wf_xxx_e_xxx_n_aaa_r AS SELECT ...        │
  │     Table now exists in PostgreSQL with data rows.                         │
  │                                                                            │
  │  2. REFERENCED                                                             │
  │     Downstream node uses this table as input:                              │
  │       CREATE TABLE wf_xxx_e_xxx_n_bbb_r AS                                │
  │         SELECT src.* FROM wf_xxx_e_xxx_n_aaa_r src JOIN ...               │
  │                                                                            │
  │  3. QUERYABLE                                                              │
  │     User inspects results via API:                                         │
  │       GET .../nodes/{nodeId}/results                                       │
  │       → SELECT * FROM wf_xxx_e_xxx_n_aaa_r LIMIT 100                      │
  │                                                                            │
  │  4. PERSISTS                                                               │
  │     Tables remain in PostgreSQL after execution completes.                 │
  │     This allows post-execution inspection and debugging.                   │
  │     Table name is stored in NodeExecutionResult.resultTableName.           │
  │                                                                            │
  │  Naming:  wf_<wfId_8>_e_<execId_8>_n_<nodeId_8>_r                        │
  │  Example: wf_550e8400_e_abc12345_n_node1234_r                             │
  │                                                                            │
  │  Each execution creates its OWN set of tables. Re-running a workflow      │
  │  produces new tables (different execId), so previous results are           │
  │  preserved.                                                                │
  └────────────────────────────────────────────────────────────────────────────┘
```

### 6.8 Execution Persistence — What Gets Stored

```
  ┌──────────────────────────────────────────────────────────────────────┐
  │                                                                      │
  │  workflow_execution                     node_execution_result         │
  │  ┌─────────────────────────┐           ┌──────────────────────────┐ │
  │  │ id: exec-uuid           │     ┌────►│ id: ner-uuid-1           │ │
  │  │ workflow_id: wf-uuid    │     │     │ execution_id: exec-uuid  │ │
  │  │ status: SUCCESS         │     │     │ node_id: node-A          │ │
  │  │ started_at: 10:05:00    │     │     │ node_type: START_QUERY   │ │
  │  │ completed_at: 10:05:32  │     │     │ status: SUCCESS          │ │
  │  │                         │     │     │ input_record_count: null  │ │
  │  │ nodeResults ────────────│─────┤     │ output_record_count:10000│ │
  │  │                         │     │     │ result_table_name:       │ │
  │  └─────────────────────────┘     │     │   wf_xxx_e_xxx_n_aaa_r  │ │
  │                                  │     │ started_at: 10:05:01     │ │
  │                                  │     │ completed_at: 10:05:05   │ │
  │                                  │     └──────────────────────────┘ │
  │                                  │                                  │
  │                                  │     ┌──────────────────────────┐ │
  │                                  ├────►│ id: ner-uuid-2           │ │
  │                                  │     │ node_id: node-B          │ │
  │                                  │     │ node_type: FILTER        │ │
  │                                  │     │ status: SUCCESS          │ │
  │                                  │     │ input_record_count: 10000│ │
  │                                  │     │ filtered_count: 6200     │ │
  │                                  │     │ output_record_count: 3800│ │
  │                                  │     │ result_table_name:       │ │
  │                                  │     │   wf_xxx_e_xxx_n_bbb_r  │ │
  │                                  │     └──────────────────────────┘ │
  │                                  │                                  │
  │                                  │     ┌──────────────────────────┐ │
  │                                  └────►│ id: ner-uuid-3           │ │
  │                                        │ node_id: node-C          │ │
  │                                        │ node_type: STOP          │ │
  │                                        │ status: SUCCESS          │ │
  │                                        │ output_record_count: 3800│ │
  │                                        │ output_file_path:        │ │
  │                                        │   ./outputs/export.csv   │ │
  │                                        └──────────────────────────┘ │
  │                                                                      │
  └──────────────────────────────────────────────────────────────────────┘
```

### 6.9 Failure Handling & Recovery

```
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                          FAILURE SCENARIOS                               │
  │                                                                          │
  │  1. ACTIVITY FAILURE (e.g., bad SQL, missing table)                      │
  │     ┌────────────────────────────────────────────────────────┐           │
  │     │  Temporal retries activity (max 3 attempts)            │           │
  │     │  10s → 20s → 40s (2.0x exponential backoff)           │           │
  │     │                                                        │           │
  │     │  If all retries fail:                                  │           │
  │     │    NodeExecutionResult.status = FAILED                 │           │
  │     │    NodeExecutionResult.errorMessage = "..."            │           │
  │     │    Downstream nodes are NOT executed                   │           │
  │     │    Independent branches continue normally              │           │
  │     └────────────────────────────────────────────────────────┘           │
  │                                                                          │
  │  2. PARTIAL FAILURE (branch fails, others succeed)                       │
  │                                                                          │
  │     START_QUERY ──► SPLIT ──► FILTER_US ──► STOP_US   ✅ SUCCESS        │
  │                         │                                                │
  │                         └──► FILTER_EU ──► STOP_EU    ❌ FAILED         │
  │                                  ▲                                       │
  │                           Bad join key — SQL error                       │
  │                                                                          │
  │     WorkflowExecution.status = FAILED                                    │
  │     (because at least one node failed)                                   │
  │     But STOP_US result table and CSV are still available.                │
  │                                                                          │
  │  3. TIMEOUT (activity exceeds 10 min / 5 min for preview)               │
  │     Same as activity failure — Temporal marks it failed after timeout.   │
  │                                                                          │
  │  4. TEMPORAL SERVER RESTART                                              │
  │     Temporal persists workflow state to its own database.                 │
  │     Running workflows resume automatically after server recovery.        │
  │     Activities that were in-flight may be retried.                       │
  └──────────────────────────────────────────────────────────────────────────┘
```

### 6.10 Preview vs Full Execution

```
  ┌──────────────────────────┐         ┌──────────────────────────┐
  │     FULL EXECUTION       │         │     NODE PREVIEW         │
  ├──────────────────────────┤         ├──────────────────────────┤
  │                          │         │                          │
  │  Scope: ALL nodes        │         │  Scope: SINGLE node     │
  │                          │         │                          │
  │  Input: None (starts     │         │  Input: Parent node's   │
  │    from root nodes)      │         │    last SUCCESS result   │
  │                          │         │    table                 │
  │  Temporal Workflow:      │         │  Temporal Workflow:      │
  │    FullExecutionWorkflow │         │    PreviewWorkflow       │
  │                          │         │                          │
  │  Activity timeout:       │         │  Activity timeout:       │
  │    10 minutes            │         │    5 minutes             │
  │                          │         │                          │
  │  Creates execution with  │         │  Creates execution with  │
  │    N NodeExecutionResult │         │    1 NodeExecutionResult │
  │    records               │         │    record                │
  │                          │         │                          │
  │  Produces temp tables    │         │  Produces one temp table │
  │    for every node        │         │    for the target node   │
  │                          │         │                          │
  │  Use case: Production    │         │  Use case: Validate a   │
  │    pipeline run          │         │    node's config before  │
  │                          │         │    running the whole     │
  │                          │         │    pipeline              │
  └──────────────────────────┘         └──────────────────────────┘
```

---

## 7. REST API Specification

**Base URL:** `http://localhost:8080`

### 7.1 Workflow Endpoints

#### Create Workflow

```
POST /api/workflows
```

**Request:**

```json
{
  "name": "Customer Segmentation Pipeline",
  "createdBy": "tushar"
}
```

**Response:** `201 Created`

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Customer Segmentation Pipeline",
  "createdBy": "tushar",
  "createdAt": "2026-04-05T10:00:00Z",
  "status": "DRAFT",
  "nodeCount": 0
}
```

---

#### List Workflows

```
GET /api/workflows
```

**Response:** `200 OK`

```json
[
  {
    "id": "550e8400-...",
    "name": "Customer Segmentation Pipeline",
    "createdBy": "tushar",
    "createdAt": "2026-04-05T10:00:00Z",
    "status": "DRAFT",
    "nodeCount": 5
  }
]
```

---

#### Get Workflow Detail

```
GET /api/workflows/{id}
```

**Response:** `200 OK`

```json
{
  "id": "550e8400-...",
  "name": "Customer Segmentation Pipeline",
  "createdBy": "tushar",
  "createdAt": "2026-04-05T10:00:00Z",
  "status": "DRAFT",
  "nodes": [
    {
      "id": "node-uuid-1",
      "type": "START_QUERY",
      "parentNodeIds": [],
      "config": { "raw_sql": "SELECT * FROM leads" },
      "position": 0
    },
    {
      "id": "node-uuid-2",
      "type": "FILTER",
      "parentNodeIds": ["node-uuid-1"],
      "config": {
        "data_mart_table": "dm_customers",
        "join_key": "email",
        "mode": "JOIN",
        "conditions": { "operation": "AND", "conditions": [{ "field": "status", "operator": "=", "value": "active" }] },
        "distinct": true
      },
      "position": 1
    },
    {
      "id": "node-uuid-3",
      "type": "STOP",
      "parentNodeIds": ["node-uuid-2"],
      "config": {},
      "position": 2
    }
  ]
}
```

---

#### Save Workflow (Bulk Upsert Nodes)

```
PUT /api/workflows/{id}
```

Replaces the entire node graph in a single transaction. Nodes with an `id` matching an existing node are updated; nodes without `id` are created; existing nodes not in the request are deleted.

**Request:**

```json
{
  "nodes": [
    {
      "id": "existing-node-uuid",
      "type": "START_QUERY",
      "parentNodeIds": [],
      "config": { "raw_sql": "SELECT * FROM leads" },
      "position": 0
    },
    {
      "id": null,
      "type": "FILTER",
      "parentNodeIds": ["existing-node-uuid"],
      "config": { "data_mart_table": "dm_customers", "join_key": "email", "mode": "JOIN" },
      "position": 1
    }
  ]
}
```

**Response:** `200 OK` — `WorkflowDetailResponse` with updated nodes.

---

#### Delete Workflow

```
DELETE /api/workflows/{id}
```

**Response:** `204 No Content`

Cascades to all nodes and executions.

---

### 7.2 Node Endpoints

#### Add Node

```
POST /api/workflows/{workflowId}/nodes
```

**Request:**

```json
{
  "parentNodeIds": ["parent-node-uuid"],
  "type": "FILTER",
  "config": { "data_mart_table": "dm_customers", "join_key": "email", "mode": "JOIN" },
  "position": 2
}
```

**Response:** `201 Created` — `NodeResponse`

---

#### Update Node

```
PUT /api/workflows/{workflowId}/nodes/{nodeId}
```

**Request:**

```json
{
  "parentNodeIds": ["new-parent-uuid"],
  "config": { "data_mart_table": "dm_customers", "join_key": "email", "mode": "SUBQUERY" }
}
```

Both fields are optional — only provided fields are updated.

**Response:** `200 OK` — `NodeResponse`

---

#### Delete Node

```
DELETE /api/workflows/{workflowId}/nodes/{nodeId}
```

**Response:** `204 No Content`

Recursively deletes all descendant nodes (nodes that have this node in their `parentNodeIds`, transitively).

---

### 7.3 Execution Endpoints

#### Execute Workflow

```
POST /api/workflows/{id}/execute
```

Starts an asynchronous full workflow execution via Temporal. Returns immediately.

**Response:** `200 OK`

```json
{
  "id": "exec-uuid",
  "workflowId": "workflow-uuid",
  "status": "RUNNING",
  "startedAt": "2026-04-05T10:05:00Z",
  "completedAt": null
}
```

---

#### List Workflow Executions

```
GET /api/workflows/{id}/executions
```

**Response:** `200 OK` — Ordered by `startedAt` descending.

```json
[
  {
    "id": "exec-uuid",
    "workflowId": "workflow-uuid",
    "status": "SUCCESS",
    "startedAt": "2026-04-05T10:05:00Z",
    "completedAt": "2026-04-05T10:05:32Z"
  }
]
```

---

#### Get Execution Detail

```
GET /api/workflows/{id}/executions/{execId}
```

**Response:** `200 OK`

```json
{
  "id": "exec-uuid",
  "workflowId": "workflow-uuid",
  "status": "SUCCESS",
  "startedAt": "2026-04-05T10:05:00Z",
  "completedAt": "2026-04-05T10:05:32Z",
  "nodeResults": [
    {
      "nodeId": "node-uuid-1",
      "nodeType": "START_QUERY",
      "status": "SUCCESS",
      "inputRecordCount": null,
      "filteredRecordCount": null,
      "outputRecordCount": 5000,
      "resultTableName": "wf_550e8400_e_abc12345_n_node1234_r",
      "outputFilePath": null,
      "errorMessage": null
    },
    {
      "nodeId": "node-uuid-2",
      "nodeType": "FILTER",
      "status": "SUCCESS",
      "inputRecordCount": 5000,
      "filteredRecordCount": 3200,
      "outputRecordCount": 1800,
      "resultTableName": "wf_550e8400_e_abc12345_n_node5678_r",
      "outputFilePath": null,
      "errorMessage": null
    },
    {
      "nodeId": "node-uuid-3",
      "nodeType": "STOP",
      "status": "SUCCESS",
      "inputRecordCount": 1800,
      "filteredRecordCount": null,
      "outputRecordCount": 1800,
      "resultTableName": null,
      "outputFilePath": "./outputs/export.csv",
      "errorMessage": null
    }
  ]
}
```

---

#### List All Executions (Cross-Workflow)

```
GET /api/executions
```

**Response:** `200 OK`

```json
[
  {
    "executionId": "exec-uuid",
    "workflowId": "workflow-uuid",
    "workflowName": "Customer Segmentation Pipeline",
    "status": "SUCCESS",
    "startedAt": "2026-04-05T10:05:00Z",
    "completedAt": "2026-04-05T10:05:32Z",
    "totalNodes": 3,
    "passedNodes": 3,
    "failedNodes": 0
  }
]
```

---

#### Get Node Results (Row Data)

```
GET /api/workflows/{id}/executions/{execId}/nodes/{nodeId}/results
```

Returns up to 100 rows from the node's temporary result table.

**Response:** `200 OK`

```json
[
  { "customer_id": "C001", "email": "alice@example.com", "score": "95" },
  { "customer_id": "C002", "email": "bob@example.com", "score": "72" }
]
```

---

#### Download Node Output (CSV)

```
GET /api/workflows/{id}/executions/{execId}/nodes/{nodeId}/download
```

Downloads the CSV file generated by a STOP node.

**Response:** `200 OK` — `application/octet-stream` with CSV content.

---

### 7.4 Preview Endpoints

#### Preview Node Output

```
POST /api/workflows/{id}/nodes/{nodeId}/preview
```

Executes a single node asynchronously using the parent node's most recent successful result as input.

**Response:** `200 OK`

```json
{
  "executionId": "preview-exec-uuid",
  "workflowId": "workflow-uuid",
  "nodeId": "node-uuid",
  "status": "RUNNING"
}
```

Poll `GET /api/workflows/{id}/executions/{preview-exec-uuid}` until complete.

---

#### SQL Preview (No Execution)

```
POST /api/workflows/{id}/nodes/{nodeId}/sql-preview
```

Generates the SQL that would be executed for a node, without running it.

**Request:**

```json
{
  "nodeType": "FILTER",
  "config": {
    "data_mart_table": "dm_customers",
    "join_key": "email",
    "mode": "JOIN",
    "conditions": { "operation": "AND", "conditions": [{ "field": "status", "operator": "=", "value": "active" }] }
  }
}
```

**Response:** `200 OK`

```json
{
  "sql": "SELECT DISTINCT src.* FROM <parent_output> src JOIN dm_customers dm ON src.email::text = dm.email::text WHERE status = 'active'"
}
```

Supports: `START_QUERY`, `FILTER`, `ENRICH`, `ENRICHMENT`.

---

### 7.5 Data Mart Endpoints

#### List Data Marts

```
GET /api/data-marts
```

**Response:** `200 OK`

```json
[
  {
    "id": "dm-uuid",
    "tableName": "dm_customers",
    "schemaName": "public",
    "description": "Customer master data",
    "columnCount": 12
  }
]
```

---

#### Get Data Mart Detail

```
GET /api/data-marts/{id}
```

**Response:** `200 OK`

```json
{
  "id": "dm-uuid",
  "tableName": "dm_customers",
  "schemaName": "public",
  "description": "Customer master data",
  "columns": [
    { "id": "col-uuid", "columnName": "customer_id", "dataType": "VARCHAR", "description": "Primary key", "ordinalPosition": 1 },
    { "id": "col-uuid", "columnName": "email", "dataType": "VARCHAR", "description": "Email address", "ordinalPosition": 2 },
    { "id": "col-uuid", "columnName": "segment", "dataType": "VARCHAR", "description": "Customer segment", "ordinalPosition": 3 }
  ]
}
```

---

### 7.6 API Summary Table

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/workflows` | Create workflow |
| `GET` | `/api/workflows` | List workflows |
| `GET` | `/api/workflows/{id}` | Get workflow with nodes |
| `PUT` | `/api/workflows/{id}` | Bulk save nodes |
| `DELETE` | `/api/workflows/{id}` | Delete workflow |
| `POST` | `/api/workflows/{id}/nodes` | Add single node |
| `PUT` | `/api/workflows/{id}/nodes/{nodeId}` | Update node |
| `DELETE` | `/api/workflows/{id}/nodes/{nodeId}` | Delete node + descendants |
| `POST` | `/api/workflows/{id}/execute` | Start full execution |
| `GET` | `/api/workflows/{id}/executions` | List workflow executions |
| `GET` | `/api/workflows/{id}/executions/{execId}` | Get execution detail |
| `GET` | `/api/workflows/{id}/executions/{execId}/nodes/{nodeId}/results` | Get node row data |
| `GET` | `/api/workflows/{id}/executions/{execId}/nodes/{nodeId}/download` | Download CSV |
| `POST` | `/api/workflows/{id}/nodes/{nodeId}/preview` | Preview single node |
| `POST` | `/api/workflows/{id}/nodes/{nodeId}/sql-preview` | SQL preview |
| `GET` | `/api/executions` | List all executions |
| `GET` | `/api/data-marts` | List data marts |
| `GET` | `/api/data-marts/{id}` | Get data mart detail |

---

## 8. Temporal Workflow Engine

### 8.1 Configuration

| Setting | Value |
|---------|-------|
| Task Queue | `segment-workflow-queue` |
| Server | `localhost:7233` |
| Namespace | `default` |
| Activity Timeout | 10 min (full), 5 min (preview) |
| Retry Policy | Max 3 attempts, 10s initial, 2.0x backoff |

### 8.2 Workflows

#### FullExecutionWorkflow

Executes the entire workflow DAG asynchronously.

**Input:** `FullExecutionInput`

| Field | Type | Description |
|-------|------|-------------|
| `workflowId` | String | Workflow UUID |
| `executionId` | String | Execution UUID |
| `graph` | List\<GraphNode\> | All nodes with type, config, parent IDs |

**Algorithm:**

1. Build adjacency maps from node parent lists
2. Identify root nodes (no parents)
3. Execute nodes in topological order using Temporal `Async.function()` and `Promise.allOf()`
4. Each node receives its parent's result table as input
5. SPLIT passes through to all children; JOIN waits for all parents
6. Collect all `NodeResult` objects
7. Call `completeExecution()` activity to persist results

#### PreviewWorkflow

Executes a single node using a provided source table.

**Input:** `PreviewInput`

| Field | Type | Description |
|-------|------|-------------|
| `workflowId` | String | Workflow UUID |
| `executionId` | String | Execution UUID |
| `nodeId` | String | Target node UUID |
| `nodeType` | String | Node type |
| `config` | Map | Node config |
| `sourceTable` | String | Parent node's result table |

### 8.3 Activities

| Activity | Input | Output | Description |
|----------|-------|--------|-------------|
| `fileUploadActivity` | FileUploadInput | FileUploadResult | Parse CSV, create table, bulk insert |
| `startQueryActivity` | StartQueryInput | StartQueryResult | Execute raw SQL via CREATE TABLE AS |
| `filterActivity` | FilterInput | FilterResult | JOIN/SUBQUERY filter with conditions |
| `enrichActivity` | EnrichInput | EnrichResult | Legacy ADD_COLUMNS/ADD_RECORDS |
| `enrichmentActivity` | EnrichmentInput | EnrichmentResult | Advanced multi-source enrichment |
| `stopNodeActivity` | StopNodeInput | StopNodeResult | Export to CSV |
| `completeExecution` | executionId, nodeResults, status | void | Persist results, update execution status |

### 8.4 Result Table Naming

Each node creates a temporary table named:

```
wf_<wfId_8chars>_e_<execId_8chars>_n_<nodeId_8chars>_r
```

UUID components truncated to 8 characters to stay within PostgreSQL's 63-character identifier limit.

---

## 9. SQL Generation

### 9.1 SqlConditionBuilder

Recursively converts nested condition objects into SQL WHERE clauses.

**Input structure:**

```json
{
  "operation": "AND",
  "conditions": [
    { "field": "age", "operator": ">", "value": "25" },
    {
      "operation": "OR",
      "conditions": [
        { "field": "region", "operator": "=", "value": "US" },
        { "field": "region", "operator": "=", "value": "EU" }
      ]
    }
  ]
}
```

**Output:** `(age > 25 AND (region = 'US' OR region = 'EU'))`

### 9.2 SqlPreviewService

Generates preview SQL for supported node types:

| Node Type | SQL Output |
|-----------|------------|
| `START_QUERY` | Returns `raw_sql` from config directly |
| `FILTER` | JOIN or SUBQUERY pattern with WHERE conditions |
| `ENRICH` | LEFT JOIN or UNION ALL pattern |
| `ENRICHMENT` | CTE-based multi-source SQL (see §4.5) |

---

## 10. Execution Lifecycle

### 10.1 Full Workflow Execution

```
User clicks "Execute"
        │
        ▼
POST /api/workflows/{id}/execute
        │
        ▼
ExecutionService.executeWorkflow()
  ├── Create WorkflowExecution (RUNNING)
  ├── Convert nodes → GraphNode list
  └── WorkflowClient.start(FullExecutionWorkflow, input)
        │
        ▼ (async, Temporal Worker)
FullExecutionWorkflowImpl.execute()
  ├── Build adjacency maps
  ├── Find root nodes
  └── For each node (topological order, parallel where possible):
      ├── Wait for parent promises
      ├── Call appropriate activity
      ├── Create NodeResult
      └── Resolve promise for children
        │
        ▼
completeExecution() activity
  ├── Persist NodeExecutionResult entities
  ├── Update WorkflowExecution status
  └── Set completedAt
        │
        ▼
User polls GET /api/workflows/{id}/executions/{execId}
  └── Sees status transition: RUNNING → SUCCESS/FAILED
```

### 10.2 Node Preview

```
User clicks "Preview" on a node
        │
        ▼
POST /api/workflows/{id}/nodes/{nodeId}/preview
        │
        ▼
ExecutionService.previewNode()
  ├── Find parent node's latest SUCCESS result
  ├── Get that result's table name as sourceTable
  ├── Create WorkflowExecution (RUNNING)
  └── WorkflowClient.start(PreviewWorkflow, input)
        │
        ▼ (async, Temporal Worker)
PreviewWorkflowImpl.execute()
  ├── Execute single node activity with sourceTable
  └── completeExecution() with single result
        │
        ▼
User polls for completion, then:
GET .../nodes/{nodeId}/results → first 100 rows
```

---

## 11. End-to-End Workflow Flow

This section traces a complete user journey — from creating a workflow to downloading the final CSV — showing every API call, system interaction, and data transformation along the way.

### 11.1 Example Scenario

> **Goal:** Ingest a list of raw leads via SQL, filter to only those matching active customers in a data mart, enrich with profile data, and export to CSV.

### 11.2 Workflow DAG

```
┌─────────────────────┐
│  START_QUERY        │   "SELECT * FROM raw_leads"
│  (node-A)           │   → creates temp table with 10,000 rows
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  FILTER             │   JOIN with dm_customers on email
│  (node-B)           │   WHERE status = 'active'
│                     │   → 10,000 in / 6,200 filtered / 3,800 out
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  ENRICHMENT         │   LINK: add loyalty_tier from dm_profiles
│  (node-C)           │   COLLECTION: COUNT orders, SUM spend
│                     │   EXPRESSION: spend_bucket CASE
│                     │   → 3,800 rows + 4 new columns
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  STOP               │   Export to CSV
│  (node-D)           │   → ./outputs/enriched-leads.csv (3,800 rows)
└─────────────────────┘
```

### 11.3 Full Sequence Diagram

```
 ┌────────┐          ┌──────────┐        ┌──────────┐       ┌──────────┐      ┌──────────┐
 │Frontend│          │ REST API │        │ Services │       │ Temporal │      │PostgreSQL│
 └───┬────┘          └────┬─────┘        └────┬─────┘       └────┬─────┘      └────┬─────┘
     │                    │                   │                  │                  │
     │  ── PHASE 1: CREATE WORKFLOW ──────────────────────────────────────────────  │
     │                    │                   │                  │                  │
     │ POST /api/workflows│                   │                  │                  │
     │ {name, createdBy}  │                   │                  │                  │
     │───────────────────►│                   │                  │                  │
     │                    │ createWorkflow()  │                  │                  │
     │                    │──────────────────►│                  │                  │
     │                    │                   │   INSERT segment_workflow (DRAFT)   │
     │                    │                   │────────────────────────────────────►│
     │                    │                   │                  │    workflow_id   │
     │                    │                   │◄────────────────────────────────────│
     │   201 {id, DRAFT}  │                   │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │  ── PHASE 2: BUILD THE DAG ────────────────────────────────────────────────  │
     │                    │                   │                  │                  │
     │ PUT /api/workflows/{id}                │                  │                  │
     │ {nodes: [A,B,C,D]} │                   │                  │                  │
     │───────────────────►│                   │                  │                  │
     │                    │ saveWorkflow()    │                  │                  │
     │                    │──────────────────►│                  │                  │
     │                    │                   │  For each node:                     │
     │                    │                   │  - Match by ID → update             │
     │                    │                   │  - No ID → create                   │
     │                    │                   │  - Missing → delete                 │
     │                    │                   │   UPSERT nodes + parent_ids         │
     │                    │                   │────────────────────────────────────►│
     │                    │                   │                  │          OK      │
     │                    │                   │◄────────────────────────────────────│
     │ 200 {nodes:[A,B,C,D]}                  │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │  ── PHASE 3: PREVIEW (OPTIONAL) ───────────────────────────────────────────  │
     │                    │                   │                  │                  │
     │ POST .../nodes/B/sql-preview           │                  │                  │
     │ {nodeType, config} │                   │                  │                  │
     │───────────────────►│                   │                  │                  │
     │                    │ generatePreview() │                  │                  │
     │                    │──────────────────►│                  │                  │
     │                    │                   │  Build SQL from config              │
     │                    │                   │  (no DB execution)                  │
     │  200 {sql: "..."}  │                   │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │ POST .../nodes/B/preview               │                  │                  │
     │───────────────────►│                   │                  │                  │
     │                    │ previewNode()     │                  │                  │
     │                    │──────────────────►│                  │                  │
     │                    │                   │  Find node-A's last SUCCESS result  │
     │                    │                   │────────────────────────────────────►│
     │                    │                   │◄────────────────────────────────────│
     │                    │                   │  Create execution (RUNNING)         │
     │                    │                   │────────────────────────────────────►│
     │                    │                   │  Start PreviewWorkflow              │
     │                    │                   │─────────────────►│                  │
     │                    │                   │                  │  Execute node-B  │
     │                    │                   │                  │  activity only   │
     │                    │                   │                  │────────────────►│
     │                    │                   │                  │  CREATE TABLE AS │
     │                    │                   │                  │◄────────────────│
     │                    │                   │                  │  completeExec()  │
     │                    │                   │                  │────────────────►│
     │ 200 {execId, RUNNING}                  │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │ [poll] GET .../executions/{execId}     │                  │                  │
     │───────────────────►│                   │                  │                  │
     │ 200 {status: SUCCESS, nodeResults}     │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │ GET .../nodes/B/results                │                  │                  │
     │───────────────────►│                   │                  │                  │
     │                    │ getNodeResults()  │                  │                  │
     │                    │──────────────────►│    SELECT * FROM temp LIMIT 100    │
     │                    │                   │────────────────────────────────────►│
     │ 200 [{row}, ...]   │                   │◄────────────────────────────────────│
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │  ── PHASE 4: EXECUTE FULL WORKFLOW ────────────────────────────────────────  │
     │                    │                   │                  │                  │
     │ POST /api/workflows/{id}/execute       │                  │                  │
     │───────────────────►│                   │                  │                  │
     │                    │ executeWorkflow() │                  │                  │
     │                    │──────────────────►│                  │                  │
     │                    │                   │  Create WorkflowExecution (RUNNING) │
     │                    │                   │────────────────────────────────────►│
     │                    │                   │  Convert nodes → GraphNode list     │
     │                    │                   │  Start FullExecutionWorkflow        │
     │                    │                   │─────────────────►│                  │
     │ 200 {execId, RUNNING}                  │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │             ┌──────────────────────────────────────────────────────────┐     │
     │             │              TEMPORAL WORKER (async)                     │     │
     │             │                                                          │     │
     │             │  1. Build adjacency maps from parentNodeIds              │     │
     │             │  2. Find roots → [node-A]                               │     │
     │             │                                                          │     │
     │             │  ┌─ node-A (START_QUERY) ──────────────────────────┐     │     │
     │             │  │  startQueryActivity()                           │     │     │
     │             │  │  CREATE TABLE wf_xxx_e_xxx_n_aaa_r AS          │────────────►
     │             │  │    SELECT * FROM raw_leads                     │     │     │
     │             │  │  → 10,000 rows                                 │◄───────────
     │             │  └────────────────────────────────────────────────┘     │     │
     │             │       │                                                 │     │
     │             │       ▼                                                 │     │
     │             │  ┌─ node-B (FILTER) ──────────────────────────────┐     │     │
     │             │  │  filterActivity()                              │     │     │
     │             │  │  CREATE TABLE wf_xxx_e_xxx_n_bbb_r AS          │────────────►
     │             │  │    SELECT DISTINCT src.*                       │     │     │
     │             │  │    FROM wf_..._n_aaa_r src                    │     │     │
     │             │  │    JOIN dm_customers dm                        │     │     │
     │             │  │      ON src.email = dm.email                   │     │     │
     │             │  │    WHERE dm.status = 'active'                  │     │     │
     │             │  │  → 3,800 rows (6,200 filtered)                │◄───────────
     │             │  └────────────────────────────────────────────────┘     │     │
     │             │       │                                                 │     │
     │             │       ▼                                                 │     │
     │             │  ┌─ node-C (ENRICHMENT) ──────────────────────────┐     │     │
     │             │  │  enrichmentActivity()                          │     │     │
     │             │  │  CREATE TABLE wf_xxx_e_xxx_n_ccc_r AS          │────────────►
     │             │  │    WITH base AS (SELECT * FROM ..._n_bbb_r),  │     │     │
     │             │  │    enr_1 AS (SELECT DISTINCT ON (id) ...),    │     │     │
     │             │  │    enr_2 AS (SELECT customer_id, COUNT(*),    │     │     │
     │             │  │              SUM(amount), ... GROUP BY ...)    │     │     │
     │             │  │    SELECT base.*, enr_1.loyalty_tier,         │     │     │
     │             │  │           enr_2.total_orders, ...,            │     │     │
     │             │  │           CASE ... AS spend_bucket            │     │     │
     │             │  │    FROM base LEFT JOIN enr_1 ... enr_2 ...    │     │     │
     │             │  │  → 3,800 rows + 4 columns                    │◄───────────
     │             │  └────────────────────────────────────────────────┘     │     │
     │             │       │                                                 │     │
     │             │       ▼                                                 │     │
     │             │  ┌─ node-D (STOP) ────────────────────────────────┐     │     │
     │             │  │  stopNodeActivity()                            │     │     │
     │             │  │  SELECT * FROM wf_..._n_ccc_r                  │────────────►
     │             │  │  Write to ./outputs/enriched-leads.csv         │◄───────────
     │             │  │  → 3,800 rows exported                        │     │     │
     │             │  └────────────────────────────────────────────────┘     │     │
     │             │       │                                                 │     │
     │             │       ▼                                                 │     │
     │             │  completeExecution()                                    │     │
     │             │  ├── Persist 4 NodeExecutionResult entities             │────────►
     │             │  ├── Set WorkflowExecution.status = SUCCESS             │     │
     │             │  └── Set completedAt = now()                            │◄───────
     │             └──────────────────────────────────────────────────────────┘     │
     │                    │                   │                  │                  │
     │  ── PHASE 5: INSPECT RESULTS ──────────────────────────────────────────────  │
     │                    │                   │                  │                  │
     │ [poll] GET .../executions/{execId}     │                  │                  │
     │───────────────────►│                   │                  │                  │
     │ 200 {status: SUCCESS, nodeResults: [   │                  │                  │
     │   {nodeA: 10000 out},                  │                  │                  │
     │   {nodeB: 3800 out, 6200 filtered},    │                  │                  │
     │   {nodeC: 3800 out, +4 cols},          │                  │                  │
     │   {nodeD: 3800 out, file: ...csv}      │                  │                  │
     │ ]}                 │                   │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │ GET .../nodes/nodeC/results            │                  │                  │
     │───────────────────►│                   │                  │                  │
     │ 200 [{customer_id, email,              │                  │                  │
     │       loyalty_tier, total_orders,      │                  │                  │
     │       total_spend, spend_bucket}, ...] │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
     │  ── PHASE 6: DOWNLOAD ─────────────────────────────────────────────────────  │
     │                    │                   │                  │                  │
     │ GET .../nodes/nodeD/download           │                  │                  │
     │───────────────────►│                   │                  │                  │
     │                    │ downloadCsv()     │                  │                  │
     │                    │──────────────────►│  Read file from disk                │
     │ 200 (CSV stream)   │                   │                  │                  │
     │◄───────────────────│                   │                  │                  │
     │                    │                   │                  │                  │
 ┌───┴────┐          ┌────┴─────┐        ┌────┴─────┐       ┌────┴─────┐      ┌────┴─────┐
 │Frontend│          │ REST API │        │ Services │       │ Temporal │      │PostgreSQL│
 └────────┘          └──────────┘        └──────────┘       └──────────┘      └──────────┘
```

### 11.4 Phase Summary

| Phase | Action | API Calls | System Effect |
|-------|--------|-----------|---------------|
| **1. Create** | User names the workflow | `POST /api/workflows` | `SegmentWorkflow` persisted in DRAFT |
| **2. Build** | User drags nodes, connects edges on canvas | `PUT /api/workflows/{id}` | Nodes upserted, parentNodeIds set |
| **3. Preview** | User validates a single node before full run | `POST .../sql-preview` + `POST .../preview` + poll | Single node executed via Temporal PreviewWorkflow |
| **4. Execute** | User runs the full pipeline | `POST .../execute` + poll | All nodes executed in topological order via Temporal |
| **5. Inspect** | User examines per-node metrics and row data | `GET .../executions/{id}` + `GET .../results` | Read from execution results + temp tables |
| **6. Download** | User downloads final CSV export | `GET .../download` | File streamed from disk |

### 11.5 Data Flow Through the DAG

```
                        ┌───────────────────────────────────────────────────────────────────┐
                        │                     PostgreSQL Tables                             │
                        │                                                                   │
  raw_leads             │  wf_xxx_n_aaa_r    wf_xxx_n_bbb_r    wf_xxx_n_ccc_r              │
  ┌──────────┐          │  ┌──────────┐      ┌──────────┐      ┌──────────────────┐         │
  │email     │ START    │  │email     │ FLTR │email     │ ENRCH│email             │  STOP   │
  │name      │ QUERY    │  │name      │      │name      │      │name              │         │
  │phone     │─────────►│  │phone     │─────►│phone     │─────►│phone             │────►CSV │
  │source    │ 10,000   │  │source    │3,800 │source    │3,800 │source            │3,800    │
  │...       │ rows     │  │...       │rows  │...       │rows  │loyalty_tier  (+) │rows     │
  └──────────┘          │  └──────────┘      └──────────┘      │total_orders  (+) │         │
                        │                                       │total_spend   (+) │         │
  dm_customers          │                                       │spend_bucket  (+) │         │
  ┌──────────┐          │                                       └──────────────────┘         │
  │email     │          │              ▲                                 ▲                   │
  │status    │──────────│──────────────┘                                 │                   │
  │segment   │  (JOIN filter on                                         │                   │
  └──────────┘   status='active')                                       │                   │
                        │                                                │                   │
  dm_profiles           │                                                │                   │
  ┌──────────┐          │                                                │                   │
  │id        │──────────│────────────────────────────────────────────────┘                   │
  │loyalty   │  (LINK: add loyalty_tier)                                                    │
  │tier      │          │                                                                   │
  └──────────┘          │                         ▲                                         │
                        │                         │                                         │
  dm_orders             │                         │                                         │
  ┌──────────┐          │                         │                                         │
  │customer  │──────────│─────────────────────────┘                                         │
  │id        │  (COLLECTION: COUNT, SUM)                                                    │
  │amount    │          │                                                                   │
  │order_date│          │                                                                   │
  └──────────┘          │                                                                   │
                        └───────────────────────────────────────────────────────────────────┘

  Legend:  (+) = column added by enrichment
           ──► = data flow direction
```

### 11.6 Branching Workflow Example (SPLIT/JOIN)

```
                    ┌──────────────┐
                    │ START_QUERY  │
                    │  (10,000)    │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │    SPLIT     │
                    │  (fan-out)   │
                    └──┬───────┬───┘
                       │       │
            ┌──────────▼─┐  ┌──▼──────────┐
            │  FILTER    │  │  FILTER     │
            │  region=US │  │  region=EU  │
            │  (4,200)   │  │  (3,100)    │
            └──────┬─────┘  └──────┬──────┘
                   │               │
            ┌──────▼─────┐  ┌──────▼──────┐
            │ ENRICHMENT │  │ ENRICHMENT  │
            │ US profiles│  │ EU profiles │
            │  (4,200)   │  │  (3,100)    │
            └──────┬─────┘  └──────┬──────┘
                   │               │
                   └───────┬───────┘
                    ┌──────▼───────┐
                    │    JOIN      │
                    │  (merge)     │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │    STOP      │
                    │  → CSV       │
                    └──────────────┘

  SPLIT: passes parent table to all children (current behavior)
  JOIN:  waits for all parents, uses first parent's result table
  Future: SPLIT edges will carry conditions for per-branch filtering
```

---

## 12. Data Marts

Data marts are pre-loaded reference tables used by FILTER and ENRICHMENT nodes.

### Seed Data

Data marts are seeded from `src/main/resources/data-mart-seed.json` at application startup. Each entry defines a table, its columns, and sample data.

### Usage in Nodes

- **FILTER nodes** join the upstream working set with a data mart, applying conditions on data mart columns
- **ENRICHMENT (LINK)** joins with a data mart to add columns to the working set
- **ENRICHMENT (COLLECTION)** joins with a data mart to compute aggregates
- **ENRICHMENT (ADD_RECORDS)** appends data mart rows to the working set

### Column Metadata

The `GET /api/data-marts/{id}` endpoint returns column names and types, enabling the frontend to present dropdown selectors for join keys, filter columns, and enrichment columns.

---

## 13. Error Handling

### Response Format

All error responses follow a consistent envelope:

```json
{
  "error": "Human-readable error message",
  "status": 400
}
```

### HTTP Status Codes

| Status | Trigger |
|--------|---------|
| `400 Bad Request` | Invalid input, validation failure |
| `404 Not Found` | Workflow, node, or execution not found |
| `500 Internal Server Error` | Unexpected server-side failure |

### Custom Exceptions

| Exception | HTTP Status | When |
|-----------|-------------|------|
| `WorkflowNotFoundException` | 404 | Workflow UUID doesn't exist |
| `NodeNotFoundException` | 404 | Node UUID doesn't exist within workflow |

### Activity-Level Errors

When a Temporal activity fails after all retries:
1. `NodeExecutionResult` is persisted with `status = FAILED` and `errorMessage`
2. `WorkflowExecution.status` is set to `FAILED`
3. Other independent branches may still complete

---

## 14. Configuration

### Application Properties

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5435/segment
spring.datasource.username=segment
spring.datasource.password=segment
spring.jpa.hibernate.ddl-auto=update

# CORS
app.cors.allowed-origins=http://localhost:3000,http://localhost:3001,http://localhost:3002

# Data Mart Seed
app.data-mart.seed-file=classpath:data-mart-seed.json

# CSV Output
app.output.directory=./outputs

# Temporal
spring.temporal.connection.target=localhost:7233
```

### Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.5.12-SNAPSHOT | Application framework |
| Spring Data JPA | (managed) | Database access |
| PostgreSQL Driver | (managed) | Database connectivity |
| Temporal SDK | 1.27.0 | Workflow orchestration |
| OpenCSV | 5.9 | CSV reading/writing |
| Jackson | (managed) | JSON serialization |
| Lombok | (managed) | Boilerplate reduction |

### Infrastructure

| Service | Default | Notes |
|---------|---------|-------|
| PostgreSQL | `localhost:5435` | Via Docker Compose |
| Temporal Server | `localhost:7233` | Via Docker Compose |
| Application | `localhost:8080` | Spring Boot embedded Tomcat |

---

## 15. Planned Enhancements

### 15.1 Explicit Graph Edges

**Status:** Design approved, implementation pending.

**Motivation:** The current `parentNodeIds` list on nodes is implicit and cannot carry metadata. React Flow models edges as first-class objects, creating a frontend/backend mismatch.

**Changes:**

1. **New entity:** `SegmentWorkflowEdge` with source/target node references, handles, conditions, metadata
2. **Remove from nodes:** `parentNodeIds` and `position` fields
3. **API:** `PUT /api/workflows/{id}` will accept `nodes[]` + `edges[]` together
4. **Topology:** Edges become the single source of truth for DAG structure
5. **SPLIT routing:** Each outgoing edge carries a condition; one edge marked `isDefault = true`; conditions evaluated in `sortOrder` priority
6. **Temporal:** `GraphEdge` runtime model, edge-based DAG traversal

**Migration:** Existing `parentNodeIds` will be converted to edge entities. The `node_parent_ids` collection table will be dropped.

### 15.2 ENRICHMENT Node Type

**Status:** Spec complete, implementation pending.

Replaces the basic `ENRICH` node with a full-featured enrichment engine supporting multiple sources per node: LINK joins, COLLECTION aggregations, computed EXPRESSION columns, and ADD_RECORDS unions. See [Section 4.5](#45-enrichment-advanced) for full specification.

### 15.3 Future Roadmap

- **SPLIT condition routing** — filter data per-edge instead of pass-through
- **Edge execution metrics** — derived input/output counts at API level
- **Data lineage** — track column provenance across nodes
- **Scheduled execution** — cron-based recurring workflow runs
- **Webhook notifications** — notify external systems on execution completion
