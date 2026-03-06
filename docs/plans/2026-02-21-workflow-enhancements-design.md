# Workflow Enhancements Design

Date: 2026-02-21

## Overview

Four enhancements to the Segment Workflow Builder:
1. Local-first Save (replace per-action API persistence)
2. Execution History tab on dashboard
3. Data Mart column dropdowns in Filter/Enrich nodes
4. Read-only SQL Preview for nodes

---

## Feature 1: Local-First Save

### Problem
Every node add/edit/delete/connect immediately calls the backend API. There is no explicit "Save" action — users have no control over when changes are persisted.

### Design

**Frontend state model:**
- On page load, fetch full workflow graph from `GET /api/workflows/{id}` and hydrate local React state.
- All subsequent operations (add node, delete node, edit config, connect edges) update **local state only**.
- Track `isDirty` flag — set `true` on any change, reset to `false` after save.

**Save button:**
- Add "Save" button to the canvas toolbar. Disabled when `isDirty === false`.
- On click: send `PUT /api/workflows/{id}` with the full node graph (all nodes, configs, edges/parent relationships).
- Show loading spinner during save, success toast on completion.

**Unsaved changes guard:**
- Browser `beforeunload` prompt when `isDirty === true`.
- In-app confirmation dialog when navigating away via Next.js router.

**Backend changes:**
- New endpoint: `PUT /api/workflows/{id}` accepting full graph payload.
- Request body: `{ nodes: [{ id, type, config, parentNodeIds, position }] }`
- Service diffs against current DB state in a single transaction: delete removed nodes, update changed nodes, insert new nodes.
- Remove or deprecate individual node CRUD endpoints (POST/PUT/DELETE per node) or keep them for backward compatibility.

**Frontend cleanup:**
- Remove API calls from `handleConnect`, `onNodesDelete`, `handleConfigUpdate`, `onDrop`.
- These become purely local state mutations.

---

## Feature 2: Execution History Tab

### Problem
The dashboard only shows workflow definitions (all in DRAFT). After executing a workflow, there's no visible execution history on the home page.

### Design

**Dashboard tabs:**
- Refactor home page (`/`) into two tabs: **Workflows** | **History**
- Workflows tab = current view (unchanged).
- History tab = new view listing all executions.

**Backend:**
- New endpoint: `GET /api/executions` — returns all `WorkflowExecution` records with:
  - `executionId`, `workflowId`, `workflowName`, `status`, `startedAt`, `completedAt`, `duration`
  - Node summary: `totalNodes`, `passedNodes`, `failedNodes`
- Paginated, sorted by `startedAt` desc.
- Joins `WorkflowExecution` → `SegmentWorkflow` for name, and aggregates `NodeExecutionResult` for counts.

**History list UI:**
- Each row shows: workflow name, status badge (green=SUCCESS, red=FAILED, yellow=RUNNING), start time, duration.
- Mini node status strip: small colored dots representing each node's execution status.
- Click row → navigate to `/history/[executionId]`.

**History detail page (`/history/[executionId]`):**
- Fetch: workflow node graph + execution node results.
- Render **read-only React Flow canvas** — no drag, no edit, no node palette.
- Each node displays: type icon, name, color-coded border (green/red/gray), input/output record counts as badges.
- Click a node → read-only side panel showing: metrics (input/filtered/output counts), sample data table, error message if failed.

---

## Feature 3: Data Mart Column Dropdowns

### Problem
In Filter and Enrich node configs, the "field" input is plain text. Users must know exact column names. No autocomplete or validation.

### Design

**Column fetching:**
- When user selects a `data_mart_table` in Filter or Enrich config, fetch columns via existing `GET /api/data-marts/{id}` endpoint.
- Cache fetched columns in component state per data mart ID to avoid re-fetching.

**Filter node (ConditionBuilder):**
- Replace plain text `<Input>` for field name with `<Select>` dropdown.
- Options: `columnName (dataType)` format — e.g., `customer_id (BIGINT)`, `email (VARCHAR)`.
- If no data mart table selected: dropdown disabled, placeholder "Select a data mart first".

**Enrich node:**
- Same approach for column selector — dropdown of available columns from selected data mart.

**Data flow:**
- FilterConfig/EnrichConfig fetches columns when `dataMartTable` changes.
- Passes column list down to ConditionBuilder / column selector components.

---

## Feature 4: SQL Preview

### Problem
Users can't see what SQL will be generated from their node configuration before execution. No way to verify correctness without running the workflow.

### Design

**Backend:**
- New endpoint: `POST /api/workflows/{id}/nodes/{nodeId}/sql-preview`
- Request body: node config JSON (sent from frontend local state, may not be saved yet).
- Uses existing `SqlConditionBuilder` + node-type SQL generation to build the query string **without executing it**.
- Returns: `{ sql: "SELECT ... FROM ... WHERE ..." }`
- Placeholder `<parent_output>` for source table references (actual CTAS names only exist at runtime).
- Supported node types: FILTER, ENRICH, START_QUERY.

**Frontend:**
- Add "Preview SQL" button in FilterConfig, EnrichConfig, and QueryConfig panels.
- On click: POST current (unsaved) node config to the preview endpoint.
- Display result in a modal dialog with monospace/code-formatted text.
- Read-only, no editing. Close button to dismiss.

---

## Summary of Backend Changes

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/workflows/{id}` | PUT | Save full workflow graph |
| `/api/executions` | GET | List all executions (paginated) |
| `/api/workflows/{id}/nodes/{nodeId}/sql-preview` | POST | Generate SQL from node config |

## Summary of Frontend Changes

| Component/Page | Change |
|----------------|--------|
| Dashboard (`/`) | Add Workflows/History tabs |
| `/history/[executionId]` | New page: read-only canvas + execution details |
| Canvas toolbar | Add Save button with dirty indicator |
| Workflow page | Local-first state management, remove per-action API calls |
| FilterConfig (ConditionBuilder) | Column dropdown instead of text input |
| EnrichConfig | Column dropdown for select_columns |
| FilterConfig, EnrichConfig, QueryConfig | Add "Preview SQL" button + modal |
