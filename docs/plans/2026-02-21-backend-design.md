# Segment Workflow System — Backend Design

## Overview

A Spring Boot backend that lets users build dynamic customer segment workflows as DAGs. Users create workflows with multiple node types (file upload, raw query, filter, enrich, split, join, stop), execute them via Temporal, and download CSV outputs. The system includes a Data Mart catalog of onboarded tables used in filter and enrich operations.

## Architecture

```
┌─────────────────────────┐        ┌──────────────────────────┐
│  Spring Boot App        │  gRPC  │   Temporal Server         │
│  (port 8080)            │◄──────►│   (port 7233)             │
│                         │        │                           │
│  - REST APIs            │        │  - Workflow execution      │
│  - Temporal Client      │        │  - Task queues             │
│  - Workflows            │        │  - Event history           │
│    (PreviewWorkflow,    │        │  - Retry logic             │
│     FullExecutionWf)    │        │                           │
│  - Activities           │        ├───────────────────────────┤
│    (per node type)      │        │   Temporal Web UI          │
│  - Graph service        │        │   (port 8088)              │
│    (manages node graph  │        │                           │
│     in Postgres)        │        │                           │
└────────┬────────────────┘        └──────────┬────────────────┘
         │                                    │
         │         ┌──────────────┐           │
         └────────►│   Postgres   │◄──────────┘
                   │              │
                   │ - Data mart catalog
                   │ - Workflow metadata + graph
                   │ - Execution results + metrics
                   │ - CTAS result tables (dynamic)
                   │ - Temporal state (separate schema)
                   └──────────────┘
```

Temporal runs as a separate Docker container. The Spring Boot app uses the Temporal Java SDK. Activities execute CTAS queries in-process. The workflow graph (nodes, edges, types, configs) lives in Postgres — Temporal receives the graph as input at execution time and traverses it dynamically.

## Node Types

| Node Type | Category | Purpose | Config | Produces |
|-----------|----------|---------|--------|----------|
| `START_FILE_UPLOAD` | Start | Ingest CSV into Postgres | file path, schema mapping | CTAS table |
| `START_QUERY` | Start | Execute raw SQL as starting data | raw SQL text | CTAS table |
| `FILTER` | Transform | Filter using data mart table | data mart table, join key, mode (JOIN/SUBQUERY), conditions | CTAS table |
| `ENRICH` | Transform | Add columns (JOIN) or rows (UNION) | data mart table, mode (ADD_COLUMNS/ADD_RECORDS), join config | CTAS table |
| `SPLIT` | Structural | Fork into parallel branches | None | None |
| `JOIN` | Structural | Merge parallel branches | None | None |
| `STOP` | Terminal | Export result as downloadable CSV | output file name | CSV file |

Multiple start nodes and stop nodes are allowed. Each start node begins an independent branch. Branches can converge via JOIN nodes.

## Data Model

### `data_mart` — seed-loaded table catalog

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `table_name` | VARCHAR | Postgres table name |
| `schema_name` | VARCHAR | Postgres schema |
| `description` | TEXT | Human-readable description |

### `data_mart_column` — column metadata

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `data_mart_id` | UUID (FK) | References data_mart |
| `column_name` | VARCHAR | Column name |
| `data_type` | VARCHAR | SQL data type |
| `description` | TEXT | Human-readable description |
| `ordinal_position` | INTEGER | Column order |

### `segment_workflow` — workflow metadata

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `name` | VARCHAR | Workflow name |
| `created_by` | VARCHAR | Creator |
| `created_at` | TIMESTAMP | Creation time |
| `status` | ENUM | DRAFT, RUNNING, COMPLETED, FAILED |

### `segment_workflow_node` — the DAG graph

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `workflow_id` | UUID (FK) | References segment_workflow |
| `type` | ENUM | START_FILE_UPLOAD, START_QUERY, FILTER, ENRICH, SPLIT, JOIN, STOP |
| `parent_node_ids` | UUID[] | Array — single for most, multiple for JOIN |
| `config` | JSONB | Type-specific configuration |
| `position` | INTEGER | Sibling ordering under a SPLIT |

### `workflow_execution` — execution instances

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Instance ID (primary key) |
| `workflow_id` | UUID (FK) | References segment_workflow |
| `status` | ENUM | PENDING, RUNNING, COMPLETED, FAILED |
| `started_at` | TIMESTAMP | Execution start |
| `completed_at` | TIMESTAMP | Execution end |

### `node_execution_result` — per-node metrics scoped to instance

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `execution_id` | UUID (FK) | References workflow_execution |
| `node_id` | UUID (FK) | References segment_workflow_node |
| `input_record_count` | INTEGER | Records entering this node |
| `filtered_record_count` | INTEGER | Records filtered out (filter nodes) |
| `output_record_count` | INTEGER | Records leaving this node |
| `result_table_name` | VARCHAR | CTAS table name |
| `output_file_path` | VARCHAR | CSV path (stop nodes) |
| `status` | ENUM | PENDING, RUNNING, SUCCESS, FAILED |
| `error_message` | TEXT | Error details on failure |
| `started_at` | TIMESTAMP | Node execution start |
| `completed_at` | TIMESTAMP | Node execution end |

### Dynamic tables

**CTAS result tables:** `wf_{workflow_id}_exec_{execution_id}_node_{node_id}_result`

**CSV output files:** `./outputs/wf_{workflow_id}/exec_{execution_id}/stop_{node_id}.csv`

## Data Mart Seeding

Data mart tables and columns are loaded from a JSON seed file at application startup.

```json
{
  "data_marts": [
    {
      "table_name": "customers",
      "schema_name": "public",
      "description": "Customer master data",
      "columns": [
        {"column_name": "customer_id", "data_type": "INTEGER", "description": "Unique customer identifier"},
        {"column_name": "name", "data_type": "VARCHAR", "description": "Full name"},
        {"column_name": "email", "data_type": "VARCHAR", "description": "Email address"},
        {"column_name": "city", "data_type": "VARCHAR", "description": "City of residence"},
        {"column_name": "age", "data_type": "INTEGER", "description": "Age in years"}
      ]
    },
    {
      "table_name": "purchases",
      "schema_name": "public",
      "description": "Purchase transaction history",
      "columns": [
        {"column_name": "purchase_id", "data_type": "INTEGER", "description": "Purchase ID"},
        {"column_name": "customer_id", "data_type": "INTEGER", "description": "FK to customers"},
        {"column_name": "amount", "data_type": "DECIMAL", "description": "Purchase amount"},
        {"column_name": "purchase_date", "data_type": "DATE", "description": "Date of purchase"}
      ]
    }
  ]
}
```

On startup, the app reads this file, upserts into `data_mart` and `data_mart_column` tables.

## Example Workflow Graph

```
node_1 (START_FILE_UPLOAD: segment.csv)
  └── node_2 (FILTER: JOIN purchases ON customer_id WHERE amount > 100)
        └── node_3 (SPLIT)
              ├── node_4 (FILTER: SUBQUERY purchases WHERE purchase_count > 3)
              │     └── node_6 (ENRICH: ADD_COLUMNS from demographics ON customer_id)
              │           └── node_8 (STOP → branch_a_output.csv)
              └── node_5 (FILTER: JOIN purchases ON customer_id WHERE city = 'Mumbai')
                    └── node_7 (STOP → mumbai_customers.csv)

node_9 (START_QUERY: SELECT * FROM loyalty_members WHERE tier = 'gold')
  └── node_10 (ENRICH: ADD_RECORDS UNION with node_6 result... via JOIN node)
```

Multiple start nodes operate as independent entry points. They can converge downstream via JOIN nodes.

## API Design

### Data Mart APIs

```
GET    /api/data-marts                              — List all data mart tables
GET    /api/data-marts/{id}                         — Get table details + all columns
```

### Workflow Lifecycle

```
POST   /api/workflows                               — Create new workflow
GET    /api/workflows                               — List all workflows
GET    /api/workflows/{id}                          — Get workflow metadata + full node graph
DELETE /api/workflows/{id}                          — Delete workflow + all execution data + CTAS tables
```

### Node Management

```
POST   /api/workflows/{id}/nodes                    — Add a node (returns updated graph)
PUT    /api/workflows/{id}/nodes/{nodeId}           — Update node config or parent_node_ids
DELETE /api/workflows/{id}/nodes/{nodeId}           — Remove node + descendants
```

### Execution

```
POST   /api/workflows/{id}/execute                  — Execute full workflow (creates new execution instance)
GET    /api/workflows/{id}/executions               — List all execution instances
GET    /api/workflows/{id}/executions/{execId}      — Get execution status + per-node metrics
```

### Node Results & Downloads

```
GET    /api/workflows/{id}/executions/{execId}/nodes/{nodeId}/results   — Sample rows from CTAS table
GET    /api/workflows/{id}/executions/{execId}/nodes/{nodeId}/download  — Download stop node CSV
```

### Preview

```
POST   /api/workflows/{id}/nodes/{nodeId}/preview   — Preview single node (creates temp execution)
```

### Request/Response Examples

**Add a filter node:**
```json
POST /api/workflows/1/nodes
{
  "parent_node_ids": ["node_2"],
  "type": "FILTER",
  "config": {
    "data_mart_table": "purchases",
    "join_key": "customer_id",
    "mode": "JOIN",
    "conditions": {
      "operation": "AND",
      "conditions": [
        {"field": "amount", "operator": ">", "value": "100"},
        {"field": "purchase_date", "operator": ">=", "value": "2025-01-01"}
      ]
    }
  }
}
```

**Add an enrich node (Add Columns):**
```json
POST /api/workflows/1/nodes
{
  "parent_node_ids": ["node_4"],
  "type": "ENRICH",
  "config": {
    "data_mart_table": "demographics",
    "mode": "ADD_COLUMNS",
    "join_key": "customer_id",
    "select_columns": ["income_bracket", "education_level"]
  }
}
```

**Add an enrich node (Add Records):**
```json
POST /api/workflows/1/nodes
{
  "parent_node_ids": ["node_4"],
  "type": "ENRICH",
  "config": {
    "data_mart_table": "new_leads",
    "mode": "ADD_RECORDS"
  }
}
```

**Execution status response:**
```json
GET /api/workflows/1/executions/exec_1
{
  "id": "exec_1",
  "workflow_id": "1",
  "status": "COMPLETED",
  "started_at": "2026-02-21T10:00:00Z",
  "completed_at": "2026-02-21T10:02:30Z",
  "node_results": [
    {
      "node_id": "node_1",
      "type": "START_FILE_UPLOAD",
      "status": "SUCCESS",
      "input_record_count": 0,
      "filtered_record_count": 0,
      "output_record_count": 50000
    },
    {
      "node_id": "node_2",
      "type": "FILTER",
      "status": "SUCCESS",
      "input_record_count": 50000,
      "filtered_record_count": 37550,
      "output_record_count": 12450
    },
    {
      "node_id": "node_8",
      "type": "STOP",
      "status": "SUCCESS",
      "input_record_count": 8200,
      "filtered_record_count": 0,
      "output_record_count": 8200,
      "output_file_path": "./outputs/wf_1/exec_1/stop_node_8.csv"
    }
  ]
}
```

## Temporal Workflows & Activities

### Activities (Per Node Type)

**`fileUploadActivity`**
- Input: `{filePath, targetTable, schemaMapping}`
- Action: Read CSV → `DROP TABLE IF EXISTS` → `CREATE TABLE` → bulk insert
- Output: `{resultTable, rowCount, columns}`

**`startQueryActivity`**
- Input: `{rawSql, targetTable}`
- Action: `DROP TABLE IF EXISTS` → `CREATE TABLE AS SELECT` using raw SQL
- Output: `{resultTable, rowCount, columns}`

**`filterActivity`**
- Input: `{sourceTable, targetTable, dataMartTable, joinKey, mode, conditions}`
- Action (JOIN mode): `CREATE TABLE AS SELECT source.* FROM source JOIN dataMart ON key WHERE conditions`
- Action (SUBQUERY mode): `CREATE TABLE AS SELECT * FROM source WHERE key IN (SELECT key FROM dataMart WHERE conditions)`
- Output: `{resultTable, inputCount, filteredCount, outputCount}`

**`enrichActivity`**
- Input: `{sourceTable, targetTable, dataMartTable, mode, joinKey, selectColumns}`
- Action (ADD_COLUMNS): `CREATE TABLE AS SELECT source.*, dm.col1, dm.col2 FROM source LEFT JOIN dataMart dm ON key`
- Action (ADD_RECORDS): `CREATE TABLE AS SELECT * FROM source UNION ALL SELECT matched_cols FROM dataMart`
- Output: `{resultTable, inputCount, addedCount, outputCount}`

**`stopNodeActivity`**
- Input: `{sourceTable, outputFilePath}`
- Action: `SELECT * FROM sourceTable` → write rows to CSV file
- Output: `{filePath, rowCount}`

All activities are idempotent: `DROP TABLE IF EXISTS` before every `CREATE`.

### Temporal Workflows

**`PreviewWorkflow`** — Single node execution for interactive preview:
- Input: `{workflowId, executionId, nodeId, nodeType, config, sourceTable}`
- Calls the appropriate activity based on nodeType
- Returns: result metrics + sample rows

**`FullExecutionWorkflow`** — Traverses entire graph:
- Input: `{workflowId, executionId, graph: [{nodeId, type, parentIds, config}, ...]}`
- Finds all root nodes (nodes with no parents)
- Executes roots in parallel via `Async.function()`
- Walks the graph: each node executes after all its parents complete
- On SPLIT: spawns child branches in parallel
- On JOIN: `Promise.allOf()` — waits for all parent branches
- On STOP: calls `stopNodeActivity` to write CSV
- Records each activity's metrics to `node_execution_result`
- Returns: `{status, nodeResults: {nodeId → metrics}}`

### Retry Policy
- Max attempts: 3
- Backoff: exponential, initial interval 10s
- Non-retryable: SQL syntax errors, missing table references
- Activities are idempotent via DROP + CREATE pattern

### Graph Traversal Algorithm (FullExecutionWorkflow)

```
1. Build adjacency map from graph input
2. Find all root nodes (no parents)
3. Initialize completion map: nodeId → Promise
4. For each root node: start async execution
5. For each non-root node:
   a. Wait for all parent promises (Promise.allOf)
   b. Resolve source table from parent results
   c. Execute appropriate activity
   d. Record result in completion map
6. Wait for all leaf/stop node promises
7. Return combined results
```

## Error Handling

### Activity-level retries
- Configured via Temporal's `RetryOptions` on each activity
- 3 attempts, exponential backoff starting at 10s
- Activities are idempotent: DROP + CREATE pattern
- Non-retryable exceptions (bad SQL, missing source table) fail immediately

### Branch failure isolation
- SPLIT branches run as parallel `Async.function()` calls
- `Promise.allOf()` waits for all branches regardless of individual failures
- After JOIN: workflow collects per-branch results — successes and failures
- User sees per-node status in execution results

### Cleanup
- On workflow delete: drop all `wf_{id}_*` CTAS tables, delete CSV outputs
- On node delete: drop that node's result tables across all executions
- Partial/failed tables cleaned up by activity retries (idempotent DROP + CREATE)

## Tech Stack

- **Spring Boot 3.5** / Java 21
- **Temporal Server** — separate Docker container
- **Temporal Java SDK** — `io.temporal:temporal-sdk`
- **PostgreSQL 16** — all data + Temporal persistence (separate schemas)
- **Docker Compose** — local development (Temporal + Postgres)

## Deployment (Local Dev)

```yaml
services:
  temporal:
    image: temporalio/auto-setup:latest
    ports:
      - "7233:7233"
    environment:
      - DB=postgres12
      - DB_PORT=5432
      - POSTGRES_USER=temporal
      - POSTGRES_PWD=temporal
      - POSTGRES_SEEDS=postgres
    depends_on:
      - postgres

  temporal-ui:
    image: temporalio/ui:latest
    ports:
      - "8088:8080"
    environment:
      - TEMPORAL_ADDRESS=temporal:7233

  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=segment
      - POSTGRES_USER=segment
      - POSTGRES_PASSWORD=segment
```

Spring Boot app runs separately via `./gradlew bootRun`.
