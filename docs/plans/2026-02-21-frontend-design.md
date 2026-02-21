# Segment Workflow System — Frontend Design

## Overview

A Next.js frontend for the Segment Workflow System. Provides a Data Marts catalog browser, a drag-and-drop workflow builder with 7 node types, GUI configuration panels for each node type, incremental preview, full workflow execution with per-node record metrics, and CSV download from stop nodes.

No authentication or authorization.

## Tech Stack

- Next.js 16 (App Router)
- React Flow (@xyflow/react v12) — node-based canvas
- Tailwind CSS v4 — styling
- shadcn/ui — component library
- dagre — automatic DAG layout
- lucide-react — icons

## Architecture

```
┌─────────────────┐         ┌─────────────────┐
│  Next.js (3000) │ ──API──►│ Spring Boot     │
│  Client-side    │         │ (8080)          │
│  React app      │◀──JSON──│ REST endpoints  │
└─────────────────┘         └─────────────────┘
```

- Frontend is a thin client over the REST API
- All API calls from client components via fetch
- No BFF, no server-side data fetching for API data
- App Router used for routing and layouts only
- Interactive pages (canvas, query builder) are client components

## Navigation

Top navbar with two tabs:

| Tab | Route | Description |
|-----|-------|-------------|
| **Workflows** | `/` | Dashboard — workflow card grid |
| **Data Marts** | `/data-marts` | Table catalog browser |

## Pages

### 1. Dashboard (`/`)

- Card grid listing all workflows
- Each card shows: name, created by, created date, status badge, node count
- Actions per card: Open (navigate to canvas), Delete (with confirmation dialog)
- "New Workflow" button → dialog with name + optional createdBy → `POST /api/workflows` → navigate to `/workflow/[id]`
- Empty state with "Create your first workflow" CTA

### 2. Data Marts (`/data-marts`)

**Table list view:**
- Card grid or table listing all data mart tables
- Each entry shows: table name, description, column count
- Search/filter by table name

**Table detail view** (click a table → expand inline or navigate to `/data-marts/[id]`):
- Table name + description at top
- Column table with columns: Name, Data Type, Description
- Read-only view — no editing (seed file manages this)

### 3. Canvas Editor (`/workflow/[id]`)

Three-panel layout:

```
┌──────────────────────────────────────────────────┐
│ ◀ Back  │  Workflow Name    │    [Execute All]   │  ← Toolbar
├─────┬───────────────────────────────┬────────────┤
│     │                               │            │
│  N  │                               │   Side     │
│  o  │       React Flow Canvas       │   Panel    │
│  d  │                               │   (400px)  │
│  e  │                               │            │
│     │                               │            │
├─────┴───────────────────────────────┴────────────┤
│  Console Panel (collapsible, ~250px)             │
└──────────────────────────────────────────────────┘
```

**Toolbar:** Back arrow (→ dashboard), workflow name (display), "Execute All" button

**Node Palette (left):** Floating panel with draggable node types, grouped:
- **Start:** File Upload, Query
- **Transform:** Filter, Enrich
- **Flow:** Split, Join
- **Terminal:** Stop

**Canvas (center):** React Flow with auto-layout via dagre. Nodes show type icon, label, config summary, execution status badge, and post-execution metrics.

**Side Panel (right):** Opens when a node is selected. Shows node config form based on type. Closes on deselect.

**Console (bottom):** Collapsible. Shows execution logs, result data tables, combined metrics, error messages.

## Node Types on Canvas

Each node type renders as a custom React Flow node:

| Type | Icon | Display | Status Badge |
|------|------|---------|-------------|
| START_FILE_UPLOAD | Upload | "File Upload" + file name | idle/running/success/failed |
| START_QUERY | Database | "Query" + truncated SQL preview | idle/running/success/failed |
| FILTER | Filter | "Filter" + data mart table name + condition count | idle/running/success/failed |
| ENRICH | Plus | "Enrich" + mode label + data mart table name | idle/running/success/failed |
| SPLIT | GitFork | "Split" | structural (no execution) |
| JOIN | Merge | "Join" | structural (no execution) |
| STOP | Download | "Stop" + download button (post-execution) | idle/running/success/failed |

### Post-Execution Metrics on Nodes

After workflow execution completes, each executed node displays small metrics below its label:
- **Input:** X records
- **Output:** Y records
- **Filtered:** Z records (filter nodes only)

Colored text — green for output, red/muted for filtered count.

## Side Panel — Node Configuration

### START_FILE_UPLOAD
- File path input (text field)
- "Preview" button

### START_QUERY
- Textarea for raw SQL query (monospace font, resizable)
- "Preview" button

### FILTER
- **Data mart table** dropdown (populated from `GET /api/data-marts`)
- **Mode** toggle: "JOIN" or "Subquery"
- **Join key** input (visible in JOIN mode): text field for the join column name
- **Conditions** builder: recursive AND/OR group builder
  - Root is always a group with operation (AND|OR)
  - Each item is a leaf condition `{field, operator, value}` or nested group
  - Field dropdown: populated from data mart table columns + source table columns
  - Operators: `=`, `!=`, `>`, `<`, `>=`, `<=`, `LIKE`, `NOT LIKE`, `IN`, `NOT IN`, `BETWEEN`, `IS NULL`, `IS NOT NULL`
  - Value input adapts by operator:
    - Single text field for most operators
    - Comma-separated input for IN/NOT IN (stored as array)
    - Two fields for BETWEEN (min/max)
    - No value for IS NULL/IS NOT NULL
  - Add condition / Add group / Remove buttons
  - Nesting depth: up to 3 levels
- "Preview" button

### ENRICH
- **Data mart table** dropdown
- **Mode** toggle: "Add Columns" (JOIN) or "Add Records" (UNION)
- **Add Columns mode:**
  - Join key input
  - Multi-select of columns to add from the data mart table (populated from `GET /api/data-marts/{id}`)
- **Add Records mode:**
  - Column mapping display (auto-matched by name between source and data mart)
- "Preview" button

### SPLIT
- Label: "Parallel Split"
- No configuration
- Preview disabled

### JOIN
- Label: "Join / Wait for all"
- No configuration
- Preview disabled

### STOP
- Output file name input (text field, defaults to `stop_{nodeId}.csv`)
- After execution: download button for the CSV (calls `/download` endpoint)

## Console Panel

Collapsible bottom panel, collapsed by default.

### Single Node Preview
1. Console auto-expands on execution start
2. Log entry: `[HH:MM:SS] Executing node #4 (FILTER)...` with spinner
3. On success: `[HH:MM:SS] Node #4 completed — 247 rows (input: 500, filtered: 253)`
4. Data table below showing columns + sample rows (shadcn Table component)
5. On failure: red error message with details

### Full Workflow Execution
1. Console auto-expands, all node badges reset to "running"
2. Spinner: "Executing workflow..."
3. On completion: **combined metrics table** showing all nodes:

| Node | Type | Input Records | Filtered | Output Records | Status |
|------|------|---------------|----------|----------------|--------|
| node_1 | FILE_UPLOAD | — | — | 50,000 | Success |
| node_2 | FILTER | 50,000 | 37,550 | 12,450 | Success |
| node_4 | FILTER | 12,450 | 4,250 | 8,200 | Success |
| node_6 | ENRICH | 8,200 | — | 8,200 | Success |
| node_8 | STOP | 8,200 | — | 8,200 | Success [Download] |

4. Click any node row → fetch sample data → show in data table below
5. Stop nodes show inline download link
6. Failed nodes show red status with error message

## Interaction Flows

### Building a Workflow
1. Drag node type from palette onto canvas → `POST /api/workflows/{id}/nodes`
2. Draw edge from node A output → node B input → `PUT /api/workflows/{id}/nodes/{nodeB}` updating `parentNodeIds`
3. Click node → side panel opens with config form
4. Edit config → auto-saves via `PUT /api/workflows/{id}/nodes/{nodeId}`
5. Delete edge → update child's `parentNodeIds` to remove parent
6. Delete node → `DELETE /api/workflows/{id}/nodes/{nodeId}`

### Incremental Preview
1. Click "Preview" on a Start node → execute → see sample rows in console
2. Open downstream Filter/Enrich node → configure using data mart table
3. Click "Preview" → see filtered/enriched results with metrics
4. Iterate: tweak conditions, preview again

### Full Execution
1. Click "Execute All" in toolbar
2. All badges reset, console shows spinner
3. On response: badges update per node with metrics, console shows combined metrics table
4. Click any node → fetch results → see sample data in console
5. Click download on stop nodes to get CSV

### SPLIT/JOIN
1. Add Split as child of a node → multiple output handles appear
2. Add nodes as children of Split (each becomes a branch)
3. Add Join node → connect branch ends to Join (multiple `parentNodeIds`)
4. Canvas auto-layouts the fork/join structure via dagre

## Frontend Project Structure

```
frontend/
├── app/
│   ├── layout.tsx                     # Root layout with navbar
│   ├── page.tsx                       # Dashboard (Workflows tab)
│   ├── data-marts/
│   │   ├── page.tsx                   # Data Marts list
│   │   └── [id]/
│   │       └── page.tsx               # Data Mart detail (columns)
│   └── workflow/
│       └── [id]/
│           └── page.tsx               # Canvas editor
├── components/
│   ├── layout/
│   │   └── navbar.tsx                 # Top navbar with tabs
│   ├── dashboard/
│   │   ├── workflow-card.tsx
│   │   └── create-workflow-dialog.tsx
│   ├── data-marts/
│   │   ├── data-mart-card.tsx
│   │   └── column-table.tsx
│   ├── canvas/
│   │   ├── workflow-canvas.tsx        # React Flow wrapper
│   │   ├── node-palette.tsx           # Draggable node types (grouped)
│   │   └── nodes/
│   │       ├── start-file-upload-node.tsx
│   │       ├── start-query-node.tsx
│   │       ├── filter-node.tsx
│   │       ├── enrich-node.tsx
│   │       ├── split-node.tsx
│   │       ├── join-node.tsx
│   │       └── stop-node.tsx
│   ├── panel/
│   │   ├── side-panel.tsx
│   │   ├── file-upload-config.tsx
│   │   ├── query-config.tsx
│   │   ├── filter-config.tsx          # Data mart selector + conditions builder
│   │   ├── enrich-config.tsx          # Mode toggle + column/record config
│   │   ├── stop-config.tsx            # File name + download button
│   │   └── condition-builder.tsx      # Recursive AND/OR group builder
│   ├── console/
│   │   ├── execution-console.tsx
│   │   ├── result-table.tsx
│   │   └── metrics-table.tsx          # Combined execution metrics
│   └── ui/                            # shadcn components
├── lib/
│   ├── api.ts                         # API client (fetch wrappers)
│   └── types.ts                       # TypeScript types matching backend DTOs
└── package.json
```

## Backend API Dependencies

The frontend depends on these backend endpoints:

| Frontend Feature | Backend Endpoint |
|-----------------|-----------------|
| Dashboard | `GET /api/workflows`, `POST /api/workflows`, `DELETE /api/workflows/{id}` |
| Data Marts list | `GET /api/data-marts` |
| Data Mart detail | `GET /api/data-marts/{id}` |
| Canvas load | `GET /api/workflows/{id}` |
| Add node | `POST /api/workflows/{id}/nodes` |
| Update node | `PUT /api/workflows/{id}/nodes/{nodeId}` |
| Delete node | `DELETE /api/workflows/{id}/nodes/{nodeId}` |
| Preview node | `POST /api/workflows/{id}/nodes/{nodeId}/preview` |
| Execute workflow | `POST /api/workflows/{id}/execute` |
| Execution status + metrics | `GET /api/workflows/{id}/executions/{execId}` |
| Node sample data | `GET /api/workflows/{id}/executions/{execId}/nodes/{nodeId}/results` |
| Download CSV | `GET /api/workflows/{id}/executions/{execId}/nodes/{nodeId}/download` |
| Filter field dropdown | `GET /api/data-marts/{id}` (column list) |
