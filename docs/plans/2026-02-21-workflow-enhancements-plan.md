# Workflow Enhancements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 4 features: local-first save, execution history dashboard tab, data mart column dropdowns, and SQL preview.

**Architecture:** Local-first state management on the frontend with a new bulk-save backend endpoint. New `/api/executions` endpoint for cross-workflow history. SQL preview service that generates SQL without executing. Column dropdowns populated from existing data mart API.

**Tech Stack:** Spring Boot 3.5 (Java 21), Next.js 16, React 19, React Flow v12, PostgreSQL, Temporal

---

### Task 1: Backend — Add `PUT /api/workflows/{id}` bulk-save endpoint

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/dto/SaveWorkflowRequest.java`
- Modify: `segment/src/main/java/com/workflow/segment/service/WorkflowService.java`
- Modify: `segment/src/main/java/com/workflow/segment/controller/WorkflowController.java`

**Step 1: Create SaveWorkflowRequest DTO**

Create file `segment/src/main/java/com/workflow/segment/dto/SaveWorkflowRequest.java`:

```java
package com.workflow.segment.dto;

import java.util.List;
import java.util.Map;

public record SaveWorkflowRequest(List<SaveNodeRequest> nodes) {
    public record SaveNodeRequest(String id, String type, List<String> parentNodeIds,
                                   Map<String, Object> config, Integer position) {}
}
```

**Step 2: Add `saveWorkflow` method to WorkflowService.java**

Add after the existing `deleteWorkflow` method (after line 43):

```java
@Transactional
public WorkflowDetailResponse saveWorkflow(UUID id, SaveWorkflowRequest request) {
    SegmentWorkflow wf = workflowRepository.findById(id)
            .orElseThrow(() -> new WorkflowNotFoundException(id));

    // Get current nodes
    List<SegmentWorkflowNode> existing = new ArrayList<>(wf.getNodes());
    Set<UUID> incomingIds = new HashSet<>();

    for (SaveWorkflowRequest.SaveNodeRequest nodeReq : request.nodes()) {
        UUID nodeId = nodeReq.id() != null ? UUID.fromString(nodeReq.id()) : null;
        if (nodeId != null) incomingIds.add(nodeId);

        SegmentWorkflowNode node = nodeId != null
                ? existing.stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null)
                : null;

        if (node == null) {
            // New node
            node = new SegmentWorkflowNode();
            node.setWorkflow(wf);
            wf.getNodes().add(node);
        }

        node.setType(NodeType.valueOf(nodeReq.type()));
        node.setParentNodeIds(nodeReq.parentNodeIds() != null
                ? new ArrayList<>(nodeReq.parentNodeIds().stream().map(UUID::fromString).toList())
                : new ArrayList<>());
        node.setConfig(nodeReq.config());
        node.setPosition(nodeReq.position());
    }

    // Delete nodes not in incoming set
    existing.stream()
            .filter(n -> !incomingIds.contains(n.getId()))
            .forEach(n -> wf.getNodes().remove(n));

    wf = workflowRepository.save(wf);
    return getWorkflow(id);
}
```

Add these imports to WorkflowService.java:
```java
import com.workflow.segment.model.SegmentWorkflowNode;
import com.workflow.segment.model.NodeType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
```

**Step 3: Add PUT endpoint to WorkflowController.java**

Add after the `delete` method (after line 37):

```java
@PutMapping("/{id}")
public WorkflowDetailResponse save(@PathVariable UUID id, @RequestBody SaveWorkflowRequest request) {
    return workflowService.saveWorkflow(id, request);
}
```

Add import: `import com.workflow.segment.dto.SaveWorkflowRequest;` (already covered by `import com.workflow.segment.dto.*;`)

**Step 4: Build and verify**

Run: `cd /Users/tushar/Desktop/Segment/segment && ./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add segment/src/main/java/com/workflow/segment/dto/SaveWorkflowRequest.java \
      segment/src/main/java/com/workflow/segment/service/WorkflowService.java \
      segment/src/main/java/com/workflow/segment/controller/WorkflowController.java
git commit -m "feat: add PUT /api/workflows/{id} bulk-save endpoint"
```

---

### Task 2: Backend — Add `GET /api/executions` cross-workflow history endpoint

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/dto/ExecutionHistoryResponse.java`
- Create: `segment/src/main/java/com/workflow/segment/controller/ExecutionController.java`
- Modify: `segment/src/main/java/com/workflow/segment/service/ExecutionService.java`
- Modify: `segment/src/main/java/com/workflow/segment/repository/WorkflowExecutionRepository.java`

**Step 1: Create ExecutionHistoryResponse DTO**

Create `segment/src/main/java/com/workflow/segment/dto/ExecutionHistoryResponse.java`:

```java
package com.workflow.segment.dto;

import com.workflow.segment.model.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExecutionHistoryResponse(
    UUID executionId,
    UUID workflowId,
    String workflowName,
    ExecutionStatus status,
    Instant startedAt,
    Instant completedAt,
    int totalNodes,
    int passedNodes,
    int failedNodes
) {}
```

**Step 2: Add repository method to WorkflowExecutionRepository.java**

Add this method:

```java
List<WorkflowExecution> findAllByOrderByStartedAtDesc();
```

**Step 3: Add `listAllExecutions` method to ExecutionService.java**

Add after the existing `listExecutions` method (after line 57):

```java
@Transactional(readOnly = true)
public List<ExecutionHistoryResponse> listAllExecutions() {
    return executionRepository.findAllByOrderByStartedAtDesc().stream()
            .map(e -> {
                List<NodeExecutionResult> results = e.getNodeResults();
                int total = results.size();
                int passed = (int) results.stream().filter(r -> r.getStatus() == ExecutionStatus.SUCCESS).count();
                int failed = (int) results.stream().filter(r -> r.getStatus() == ExecutionStatus.FAILED).count();
                return new ExecutionHistoryResponse(
                        e.getId(), e.getWorkflow().getId(), e.getWorkflow().getName(),
                        e.getStatus(), e.getStartedAt(), e.getCompletedAt(),
                        total, passed, failed);
            }).toList();
}
```

Add import: `import com.workflow.segment.dto.ExecutionHistoryResponse;` (already covered by wildcard import)

**Step 4: Create ExecutionController.java**

Create `segment/src/main/java/com/workflow/segment/controller/ExecutionController.java`:

```java
package com.workflow.segment.controller;

import com.workflow.segment.dto.ExecutionHistoryResponse;
import com.workflow.segment.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {
    private final ExecutionService executionService;

    @GetMapping
    public List<ExecutionHistoryResponse> listAll() {
        return executionService.listAllExecutions();
    }
}
```

**Step 5: Build and verify**

Run: `cd /Users/tushar/Desktop/Segment/segment && ./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add segment/src/main/java/com/workflow/segment/dto/ExecutionHistoryResponse.java \
      segment/src/main/java/com/workflow/segment/controller/ExecutionController.java \
      segment/src/main/java/com/workflow/segment/service/ExecutionService.java \
      segment/src/main/java/com/workflow/segment/repository/WorkflowExecutionRepository.java
git commit -m "feat: add GET /api/executions endpoint for cross-workflow history"
```

---

### Task 3: Backend — Add SQL preview service

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/service/SqlPreviewService.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/SqlPreviewRequest.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/SqlPreviewResponse.java`
- Modify: `segment/src/main/java/com/workflow/segment/controller/WorkflowController.java`

**Step 1: Create SqlPreviewRequest DTO**

Create `segment/src/main/java/com/workflow/segment/dto/SqlPreviewRequest.java`:

```java
package com.workflow.segment.dto;

import java.util.Map;

public record SqlPreviewRequest(String nodeType, Map<String, Object> config) {}
```

**Step 2: Create SqlPreviewResponse DTO**

Create `segment/src/main/java/com/workflow/segment/dto/SqlPreviewResponse.java`:

```java
package com.workflow.segment.dto;

public record SqlPreviewResponse(String sql) {}
```

**Step 3: Create SqlPreviewService.java**

Create `segment/src/main/java/com/workflow/segment/service/SqlPreviewService.java`:

```java
package com.workflow.segment.service;

import com.workflow.segment.dto.SqlPreviewResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SqlPreviewService {

    @SuppressWarnings("unchecked")
    public SqlPreviewResponse generatePreview(String nodeType, Map<String, Object> config) {
        String sql = switch (nodeType) {
            case "START_QUERY" -> generateStartQuerySql(config);
            case "FILTER" -> generateFilterSql(config);
            case "ENRICH" -> generateEnrichSql(config);
            default -> throw new IllegalArgumentException("SQL preview not supported for node type: " + nodeType);
        };
        return new SqlPreviewResponse(sql);
    }

    private String generateStartQuerySql(Map<String, Object> config) {
        String rawSql = (String) config.getOrDefault("raw_sql", "");
        if (rawSql.isBlank()) return "-- No SQL query configured";
        return rawSql;
    }

    @SuppressWarnings("unchecked")
    private String generateFilterSql(Map<String, Object> config) {
        String dmTable = (String) config.getOrDefault("data_mart_table", "<data_mart_table>");
        String mode = (String) config.getOrDefault("mode", "JOIN");
        String joinKey = (String) config.getOrDefault("join_key", "<join_key>");
        Map<String, Object> conditions = (Map<String, Object>) config.get("conditions");
        String whereClause = SqlConditionBuilder.buildWhereClause(conditions);

        if ("JOIN".equalsIgnoreCase(mode)) {
            return String.format(
                    "SELECT src.*\nFROM <parent_output> src\nJOIN %s dm\n  ON src.%s = dm.%s\n%s",
                    dmTable, joinKey, joinKey, whereClause);
        } else {
            return String.format(
                    "SELECT *\nFROM <parent_output>\nWHERE %s IN (\n  SELECT %s FROM %s\n  %s\n)",
                    joinKey, joinKey, dmTable, whereClause);
        }
    }

    @SuppressWarnings("unchecked")
    private String generateEnrichSql(Map<String, Object> config) {
        String dmTable = (String) config.getOrDefault("data_mart_table", "<data_mart_table>");
        String mode = (String) config.getOrDefault("mode", "ADD_COLUMNS");
        String joinKey = (String) config.getOrDefault("join_key", "<join_key>");
        List<String> selectColumns = (List<String>) config.get("select_columns");

        if ("ADD_COLUMNS".equalsIgnoreCase(mode)) {
            String dmCols = selectColumns != null && !selectColumns.isEmpty()
                    ? selectColumns.stream().map(c -> "dm." + c).collect(Collectors.joining(", "))
                    : "dm.*";
            return String.format(
                    "SELECT src.*, %s\nFROM <parent_output> src\nLEFT JOIN %s dm\n  ON src.%s = dm.%s",
                    dmCols, dmTable, joinKey, joinKey);
        } else {
            return String.format(
                    "SELECT * FROM <parent_output>\nUNION ALL\nSELECT * FROM %s",
                    dmTable);
        }
    }
}
```

**Step 4: Add SQL preview endpoint to WorkflowController.java**

Add after the `previewNode` method (after line 71):

```java
@PostMapping("/{id}/nodes/{nodeId}/sql-preview")
public SqlPreviewResponse sqlPreview(@PathVariable UUID id, @PathVariable UUID nodeId,
                                      @RequestBody SqlPreviewRequest request) {
    return sqlPreviewService.generatePreview(request.nodeType(), request.config());
}
```

Also add `SqlPreviewService` as a dependency. Modify the class fields:

```java
public class WorkflowController {
    private final WorkflowService workflowService;
    private final ExecutionService executionService;
    private final SqlPreviewService sqlPreviewService;
```

**Step 5: Build and verify**

Run: `cd /Users/tushar/Desktop/Segment/segment && ./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add segment/src/main/java/com/workflow/segment/dto/SqlPreviewRequest.java \
      segment/src/main/java/com/workflow/segment/dto/SqlPreviewResponse.java \
      segment/src/main/java/com/workflow/segment/service/SqlPreviewService.java \
      segment/src/main/java/com/workflow/segment/controller/WorkflowController.java
git commit -m "feat: add SQL preview endpoint for generating SQL from node config"
```

---

### Task 4: Frontend — Add API methods for new endpoints

**Files:**
- Modify: `frontend/lib/types.ts`
- Modify: `frontend/lib/api.ts`

**Step 1: Add new types to `types.ts`**

Add at the end of the file (after line 85):

```typescript
export interface ExecutionHistoryItem {
  executionId: string;
  workflowId: string;
  workflowName: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  startedAt: string;
  completedAt: string | null;
  totalNodes: number;
  passedNodes: number;
  failedNodes: number;
}

export interface SaveWorkflowRequest {
  nodes: SaveNodeRequest[];
}

export interface SaveNodeRequest {
  id: string | null;
  type: string;
  parentNodeIds: string[];
  config: Record<string, unknown>;
  position: number | null;
}

export interface SqlPreviewResponse {
  sql: string;
}
```

**Step 2: Add new API methods to `api.ts`**

Add imports for the new types. Update the import block at the top (line 1-12) to include:

```typescript
import type {
  DataMart,
  DataMartDetail,
  Workflow,
  WorkflowDetail,
  NodeResponse,
  AddNodeRequest,
  UpdateNodeRequest,
  ExecutionResponse,
  ExecutionDetail,
  PreviewResponse,
  ExecutionHistoryItem,
  SaveWorkflowRequest,
  SqlPreviewResponse,
} from './types';
```

Update the `export type` block (lines 16-27) similarly.

Add these methods inside the `api` object (after line 86, before the closing `};`):

```typescript
  // Bulk save
  saveWorkflow: (workflowId: string, data: SaveWorkflowRequest) =>
    fetchApi<WorkflowDetail>(`/workflows/${workflowId}`, { method: 'PUT', body: JSON.stringify(data) }),

  // Execution history (cross-workflow)
  listAllExecutions: () =>
    fetchApi<ExecutionHistoryItem[]>('/executions'),

  // SQL Preview
  sqlPreview: (workflowId: string, nodeId: string, data: { nodeType: string; config: Record<string, unknown> }) =>
    fetchApi<SqlPreviewResponse>(`/workflows/${workflowId}/nodes/${nodeId}/sql-preview`, { method: 'POST', body: JSON.stringify(data) }),
```

**Step 3: Commit**

```bash
git add frontend/lib/types.ts frontend/lib/api.ts
git commit -m "feat: add frontend API methods for save, history, and SQL preview"
```

---

### Task 5: Frontend — Convert workflow editor to local-first save

**Files:**
- Modify: `frontend/app/workflow/[id]/page.tsx`
- Modify: `frontend/components/panel/side-panel.tsx`

**Step 1: Refactor workflow editor page to local-first state**

Replace the entire content of `frontend/app/workflow/[id]/page.tsx` with the following. Key changes:
- `handleAddNode`, `handleDeleteNode`, `handleConnect` now mutate local state only (no API calls)
- New `isDirty` state tracks unsaved changes
- New `handleSave` method calls `api.saveWorkflow`
- `handleConfigUpdate` callback passed to SidePanel updates local state
- `beforeunload` guard for unsaved changes

```typescript
'use client';

import { use, useState, useEffect, useCallback, useRef } from 'react';
import { api } from '@/lib/api';
import type { WorkflowDetail, NodeResponse, NodeType, ExecutionDetail, NodeExecutionResult } from '@/lib/types';
import { WorkflowCanvas } from '@/components/canvas/workflow-canvas';
import { NodePalette } from '@/components/canvas/node-palette';
import { SidePanel } from '@/components/panel/side-panel';
import { ExecutionConsole, type LogEntry, type ConsoleTab } from '@/components/console/execution-console';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Play, Loader2, Save } from 'lucide-react';
import Link from 'next/link';
import { showToast } from '@/components/ui/toast-notifications';

export default function WorkflowEditorPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [workflow, setWorkflow] = useState<WorkflowDetail | null>(null);
  const [localNodes, setLocalNodes] = useState<NodeResponse[]>([]);
  const [isDirty, setIsDirty] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Console state
  const [consoleOpen, setConsoleOpen] = useState(false);
  const [consoleTab, setConsoleTab] = useState<ConsoleTab>('log');
  const [logEntries, setLogEntries] = useState<LogEntry[]>([]);
  const [resultRows, setResultRows] = useState<Record<string, unknown>[]>([]);
  const [nodeResults, setNodeResults] = useState<NodeExecutionResult[]>([]);
  const [isExecuting, setIsExecuting] = useState(false);
  const [isPreviewing, setIsPreviewing] = useState(false);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const currentExecRef = useRef<{ workflowId: string; execId: string } | null>(null);
  const lastPollStatusRef = useRef<string>('');
  const pollErrorCountRef = useRef<number>(0);

  const addLog = useCallback((message: string, type: LogEntry['type'] = 'info') => {
    const timestamp = new Date().toLocaleTimeString();
    setLogEntries((prev) => [...prev, { timestamp, message, type }]);
  }, []);

  const loadWorkflow = useCallback(async () => {
    try {
      const data = await api.getWorkflow(id);
      setWorkflow(data);
      setLocalNodes(data.nodes);
      setIsDirty(false);
    } catch (err) {
      console.error('Failed to load workflow:', err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { loadWorkflow(); }, [loadWorkflow]);

  // Unsaved changes guard
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (isDirty) { e.preventDefault(); }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [isDirty]);

  // Cleanup polling on unmount
  useEffect(() => {
    return () => { if (pollingRef.current) clearInterval(pollingRef.current); };
  }, []);

  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  }, []);

  const pollExecution = useCallback((workflowId: string, execId: string) => {
    currentExecRef.current = { workflowId, execId };
    lastPollStatusRef.current = '';
    pollErrorCountRef.current = 0;

    pollingRef.current = setInterval(async () => {
      try {
        const detail: ExecutionDetail = await api.getExecution(workflowId, execId);
        pollErrorCountRef.current = 0;
        setNodeResults(detail.nodeResults);

        if (detail.status === 'SUCCESS') {
          stopPolling();
          setIsExecuting(false);
          addLog('Execution completed successfully.', 'success');
          setConsoleTab('metrics');
        } else if (detail.status === 'FAILED') {
          stopPolling();
          setIsExecuting(false);
          const failedNode = detail.nodeResults.find((n) => n.status === 'FAILED');
          const errMsg = failedNode?.errorMessage || 'Unknown error';
          addLog(`Execution failed: ${errMsg}`, 'error');
          setConsoleTab('metrics');
        } else {
          const running = detail.nodeResults.filter((n) => n.status === 'RUNNING').length;
          const done = detail.nodeResults.filter((n) => n.status === 'SUCCESS').length;
          const total = detail.nodeResults.length;
          const statusKey = `${done}/${total}/${running}`;
          if (statusKey !== lastPollStatusRef.current) {
            lastPollStatusRef.current = statusKey;
            addLog(`Status: ${detail.status} (${done}/${total} nodes done, ${running} running)`);
          }
        }
      } catch (err) {
        console.error('Polling error:', err);
        pollErrorCountRef.current += 1;
        if (pollErrorCountRef.current >= 5) {
          stopPolling();
          setIsExecuting(false);
          addLog('Polling stopped after 5 consecutive errors.', 'error');
        } else {
          addLog('Error polling execution status. Retrying...', 'error');
        }
      }
    }, 2000);
  }, [addLog, stopPolling]);

  // --- Local-first handlers ---

  const handleAddNode = (type: NodeType) => {
    const tempId = crypto.randomUUID();
    const newNode: NodeResponse = {
      id: tempId,
      type,
      parentNodeIds: [],
      config: getDefaultConfig(type),
      position: null,
    };
    setLocalNodes((prev) => [...prev, newNode]);
    setIsDirty(true);
  };

  const handleDeleteNode = (nodeId: string) => {
    setLocalNodes((prev) => {
      // Remove the node and clean up parent references
      const filtered = prev.filter((n) => n.id !== nodeId);
      return filtered.map((n) => ({
        ...n,
        parentNodeIds: n.parentNodeIds.filter((pid) => pid !== nodeId),
      }));
    });
    if (selectedNodeId === nodeId) setSelectedNodeId(null);
    setIsDirty(true);
  };

  const handleConnect = (sourceId: string, targetId: string) => {
    setLocalNodes((prev) =>
      prev.map((n) =>
        n.id === targetId
          ? { ...n, parentNodeIds: [...n.parentNodeIds, sourceId] }
          : n
      )
    );
    setIsDirty(true);
  };

  const handleConfigUpdate = (nodeId: string, config: Record<string, unknown>) => {
    setLocalNodes((prev) =>
      prev.map((n) => (n.id === nodeId ? { ...n, config } : n))
    );
    setIsDirty(true);
  };

  const handleSave = async () => {
    if (!workflow || isSaving) return;
    try {
      setIsSaving(true);
      const data = await api.saveWorkflow(workflow.id, {
        nodes: localNodes.map((n) => ({
          id: n.id,
          type: n.type,
          parentNodeIds: n.parentNodeIds,
          config: n.config,
          position: n.position,
        })),
      });
      setWorkflow(data);
      setLocalNodes(data.nodes);
      setIsDirty(false);
      showToast('Workflow saved', 'success');
    } catch (err) {
      console.error('Failed to save workflow:', err);
    } finally {
      setIsSaving(false);
    }
  };

  const handleExecute = async () => {
    if (!workflow || isExecuting) return;
    // Save first if dirty
    if (isDirty) {
      await handleSave();
    }
    try {
      stopPolling();
      setIsExecuting(true);
      setNodeResults([]);
      setResultRows([]);
      setLogEntries([]);
      setConsoleOpen(true);
      setConsoleTab('log');
      addLog('Starting workflow execution...');

      const exec = await api.executeWorkflow(workflow.id);
      addLog(`Execution ${exec.id} created (status: ${exec.status})`);
      pollExecution(workflow.id, exec.id);
    } catch (err) {
      setIsExecuting(false);
      const msg = err instanceof Error ? err.message : 'Unknown error';
      addLog(`Failed to start execution: ${msg}`, 'error');
    }
  };

  const handlePreview = async (nodeId: string) => {
    if (!workflow || isPreviewing || isExecuting) return;
    // Save first if dirty
    if (isDirty) {
      await handleSave();
    }
    try {
      setConsoleOpen(true);
      setConsoleTab('log');
      addLog(`Previewing node ${nodeId.slice(0, 8)}...`);
      setIsPreviewing(true);

      const preview = await api.previewNode(workflow.id, nodeId);
      addLog(`Preview execution ${preview.executionId.slice(0, 8)} started, waiting for result...`);

      let detail = null;
      for (let i = 0; i < 30; i++) {
        await new Promise((r) => setTimeout(r, 2000));
        detail = await api.getExecution(workflow.id, preview.executionId);
        if (detail.status === 'SUCCESS' || detail.status === 'FAILED') break;
      }

      setIsPreviewing(false);

      if (!detail || detail.status !== 'SUCCESS') {
        const errNode = detail?.nodeResults?.find((n) => n.status === 'FAILED');
        addLog(`Preview failed: ${errNode?.errorMessage ?? 'Timed out or unknown error'}`, 'error');
        return;
      }

      const nodeResult = detail.nodeResults.find((n) => n.nodeId === nodeId);
      const rows = await api.getNodeResults(workflow.id, preview.executionId, nodeId);

      addLog(
        `Preview complete: ${nodeResult?.inputRecordCount ?? 0} input, ${nodeResult?.filteredRecordCount ?? 0} filtered, ${nodeResult?.outputRecordCount ?? 0} output`,
        'success',
      );
      setResultRows(rows ?? []);
      setNodeResults(detail.nodeResults);
      currentExecRef.current = { workflowId: workflow.id, execId: preview.executionId };
      setConsoleTab('results');
    } catch (err) {
      setIsPreviewing(false);
      const msg = err instanceof Error ? err.message : 'Unknown error';
      addLog(`Preview failed: ${msg}`, 'error');
    }
  };

  const handleViewNodeResults = async (nodeId: string) => {
    if (!currentExecRef.current) return;
    const { workflowId, execId } = currentExecRef.current;
    try {
      addLog(`Loading results for node ${nodeId.slice(0, 8)}...`);
      const rows = await api.getNodeResults(workflowId, execId, nodeId);
      setResultRows(rows);
      setConsoleTab('results');
      addLog(`Loaded ${rows.length} rows.`, 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error';
      addLog(`Failed to load node results: ${msg}`, 'error');
    }
  };

  const handleDownloadCsv = (nodeId: string) => {
    if (!currentExecRef.current) return;
    const { workflowId, execId } = currentExecRef.current;
    const url = api.downloadCsvUrl(workflowId, execId, nodeId);
    window.open(url, '_blank');
  };

  if (loading) return <div className="p-6 text-gray-500">Loading workflow...</div>;
  if (!workflow) return <div className="p-6 text-red-500">Workflow not found</div>;

  return (
    <div className="flex flex-col h-[calc(100vh-56px)]">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-4 py-2 border-b bg-white">
        <div className="flex items-center gap-3">
          <Link href="/">
            <Button variant="ghost" size="sm">
              <ArrowLeft className="h-4 w-4" />
            </Button>
          </Link>
          <h1 className="font-semibold">{workflow.name}</h1>
          {isDirty && <span className="text-xs text-amber-500 font-medium">Unsaved changes</span>}
        </div>
        <div className="flex items-center gap-2">
          <Button onClick={handleSave} size="sm" variant="outline" disabled={!isDirty || isSaving}>
            {isSaving ? (
              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
            ) : (
              <Save className="h-4 w-4 mr-1" />
            )}
            {isSaving ? 'Saving...' : 'Save'}
          </Button>
          <Button onClick={handleExecute} size="sm" disabled={isExecuting}>
            {isExecuting ? (
              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
            ) : (
              <Play className="h-4 w-4 mr-1" />
            )}
            {isExecuting ? 'Executing...' : 'Execute All'}
          </Button>
        </div>
      </div>

      {/* Canvas area */}
      <div className="flex flex-1 relative overflow-hidden">
        <NodePalette onAddNode={handleAddNode} />
        <div className="flex-1">
          <WorkflowCanvas
            nodes={localNodes}
            onNodeSelect={setSelectedNodeId}
            onConnect={handleConnect}
            onDeleteNode={handleDeleteNode}
            workflowId={workflow.id}
            onReload={loadWorkflow}
          />
        </div>
        {selectedNodeId && (
          <SidePanel
            workflowId={workflow.id}
            nodeId={selectedNodeId}
            nodes={localNodes}
            onClose={() => setSelectedNodeId(null)}
            onDelete={handleDeleteNode}
            onConfigUpdate={handleConfigUpdate}
            onPreview={handlePreview}
          />
        )}
      </div>

      {/* Console Panel */}
      <ExecutionConsole
        open={consoleOpen}
        onToggle={() => setConsoleOpen((o) => !o)}
        logEntries={logEntries}
        resultRows={resultRows}
        nodeResults={nodeResults}
        isRunning={isExecuting || isPreviewing}
        activeTab={consoleTab}
        onTabChange={setConsoleTab}
        onViewNodeResults={handleViewNodeResults}
        onDownloadCsv={handleDownloadCsv}
      />
    </div>
  );
}

function getDefaultConfig(type: NodeType): Record<string, unknown> {
  switch (type) {
    case 'START_FILE_UPLOAD': return { file_path: '' };
    case 'START_QUERY': return { raw_sql: '' };
    case 'FILTER': return { data_mart_table: '', mode: 'JOIN', join_key: '', conditions: { operation: 'AND', conditions: [] } };
    case 'ENRICH': return { data_mart_table: '', mode: 'ADD_COLUMNS', join_key: '', select_columns: [] };
    case 'STOP': return { output_file_path: '' };
    default: return {};
  }
}
```

**Step 2: Update SidePanel to use `onConfigUpdate` callback instead of API calls**

Replace the entire content of `frontend/components/panel/side-panel.tsx`:

```typescript
'use client';

import type { NodeResponse } from '@/lib/types';
import { Button } from '@/components/ui/button';
import { X, Trash2, Play } from 'lucide-react';
import { FileUploadConfig } from './file-upload-config';
import { QueryConfig } from './query-config';
import { FilterConfig } from './filter-config';
import { EnrichConfig } from './enrich-config';
import { StopConfig } from './stop-config';

interface SidePanelProps {
  workflowId: string;
  nodeId: string;
  nodes: NodeResponse[];
  onClose: () => void;
  onDelete: (nodeId: string) => void;
  onConfigUpdate: (nodeId: string, config: Record<string, unknown>) => void;
  onPreview: (nodeId: string) => void;
}

export function SidePanel({ workflowId, nodeId, nodes, onClose, onDelete, onConfigUpdate, onPreview }: SidePanelProps) {
  const node = nodes.find((n) => n.id === nodeId);
  if (!node) return null;

  const handleConfigUpdate = (config: Record<string, unknown>) => {
    onConfigUpdate(nodeId, config);
  };

  return (
    <div className="w-[400px] border-l bg-white flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b">
        <h3 className="font-semibold text-sm">{getNodeLabel(node.type)}</h3>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="sm" onClick={() => onPreview(nodeId)} title="Preview">
            <Play className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={() => { if (confirm('Delete this node?')) onDelete(nodeId); }} title="Delete">
            <Trash2 className="h-4 w-4 text-red-500" />
          </Button>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Config form */}
      <div className="flex-1 overflow-y-auto p-4">
        {node.type === 'START_FILE_UPLOAD' && (
          <FileUploadConfig config={node.config} onUpdate={handleConfigUpdate} />
        )}
        {node.type === 'START_QUERY' && (
          <QueryConfig config={node.config} onUpdate={handleConfigUpdate} workflowId={workflowId} nodeId={nodeId} />
        )}
        {node.type === 'FILTER' && (
          <FilterConfig config={node.config} onUpdate={handleConfigUpdate} workflowId={workflowId} nodeId={nodeId} />
        )}
        {node.type === 'ENRICH' && (
          <EnrichConfig config={node.config} onUpdate={handleConfigUpdate} workflowId={workflowId} nodeId={nodeId} />
        )}
        {node.type === 'STOP' && (
          <StopConfig config={node.config} onUpdate={handleConfigUpdate} />
        )}
        {(node.type === 'SPLIT' || node.type === 'JOIN') && (
          <div className="text-sm text-gray-500">
            <p>{node.type === 'SPLIT' ? 'Parallel Split' : 'Join / Wait for all'}</p>
            <p className="mt-2 text-xs text-gray-400">This is a structural node with no configuration.</p>
          </div>
        )}
      </div>
    </div>
  );
}

function getNodeLabel(type: string): string {
  const labels: Record<string, string> = {
    START_FILE_UPLOAD: 'File Upload',
    START_QUERY: 'Query',
    FILTER: 'Filter',
    ENRICH: 'Enrich',
    SPLIT: 'Parallel Split',
    JOIN: 'Join',
    STOP: 'Stop',
  };
  return labels[type] || type;
}
```

Note: SidePanel now passes `workflowId` and `nodeId` to FilterConfig, EnrichConfig, and QueryConfig — these will be used by the SQL preview feature (Task 8).

**Step 3: Commit**

```bash
git add frontend/app/workflow/[id]/page.tsx frontend/components/panel/side-panel.tsx
git commit -m "feat: convert workflow editor to local-first save model"
```

---

### Task 6: Frontend — Dashboard with Workflows/History tabs

**Files:**
- Modify: `frontend/app/page.tsx`
- Create: `frontend/components/dashboard/execution-history-list.tsx`

**Step 1: Create ExecutionHistoryList component**

Create `frontend/components/dashboard/execution-history-list.tsx`:

```typescript
'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { ExecutionHistoryItem } from '@/lib/types';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import Link from 'next/link';

const statusColors: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  RUNNING: 'bg-blue-100 text-blue-700',
  SUCCESS: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
};

const dotColors: Record<string, string> = {
  SUCCESS: 'bg-green-500',
  FAILED: 'bg-red-500',
  RUNNING: 'bg-blue-500',
  PENDING: 'bg-gray-300',
};

function formatDuration(startedAt: string, completedAt: string | null): string {
  if (!completedAt) return 'In progress...';
  const ms = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
}

export function ExecutionHistoryList() {
  const [executions, setExecutions] = useState<ExecutionHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.listAllExecutions()
      .then(setExecutions)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="text-gray-500">Loading execution history...</p>;

  if (executions.length === 0) {
    return (
      <div className="text-center py-16">
        <p className="text-gray-500 text-lg">No executions yet</p>
        <p className="text-gray-400 mt-1">Run a workflow to see execution history here</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {executions.map((exec) => (
        <Link key={exec.executionId} href={`/history/${exec.executionId}?workflowId=${exec.workflowId}`}>
          <Card className="hover:shadow-md transition-shadow cursor-pointer">
            <CardContent className="py-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div>
                    <p className="font-medium text-sm">{exec.workflowName}</p>
                    <p className="text-xs text-gray-400">
                      {new Date(exec.startedAt).toLocaleString()} · {formatDuration(exec.startedAt, exec.completedAt)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  {/* Node status dots */}
                  <div className="flex items-center gap-1">
                    {exec.passedNodes > 0 && (
                      <div className="flex items-center gap-0.5">
                        <span className={`w-2 h-2 rounded-full ${dotColors.SUCCESS}`} />
                        <span className="text-xs text-gray-500">{exec.passedNodes}</span>
                      </div>
                    )}
                    {exec.failedNodes > 0 && (
                      <div className="flex items-center gap-0.5">
                        <span className={`w-2 h-2 rounded-full ${dotColors.FAILED}`} />
                        <span className="text-xs text-gray-500">{exec.failedNodes}</span>
                      </div>
                    )}
                    {exec.totalNodes - exec.passedNodes - exec.failedNodes > 0 && (
                      <div className="flex items-center gap-0.5">
                        <span className={`w-2 h-2 rounded-full ${dotColors.PENDING}`} />
                        <span className="text-xs text-gray-500">{exec.totalNodes - exec.passedNodes - exec.failedNodes}</span>
                      </div>
                    )}
                  </div>
                  <Badge className={statusColors[exec.status] || 'bg-gray-100'} variant="secondary">
                    {exec.status}
                  </Badge>
                </div>
              </div>
            </CardContent>
          </Card>
        </Link>
      ))}
    </div>
  );
}
```

**Step 2: Refactor dashboard page with tabs**

Replace the content of `frontend/app/page.tsx`:

```typescript
'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { Workflow } from '@/lib/types';
import { WorkflowCard } from '@/components/dashboard/workflow-card';
import { CreateWorkflowDialog } from '@/components/dashboard/create-workflow-dialog';
import { ExecutionHistoryList } from '@/components/dashboard/execution-history-list';
import { useRouter } from 'next/navigation';

type DashboardTab = 'workflows' | 'history';

export default function DashboardPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<DashboardTab>('workflows');
  const router = useRouter();

  const loadWorkflows = async () => {
    try {
      const data = await api.listWorkflows();
      setWorkflows(data);
    } catch (err) {
      console.error('Failed to load workflows:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadWorkflows(); }, []);

  const handleCreate = async (name: string, createdBy: string) => {
    try {
      const wf = await api.createWorkflow({ name, createdBy: createdBy || undefined });
      router.push(`/workflow/${wf.id}`);
    } catch (err) {
      console.error('Failed to create workflow:', err);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await api.deleteWorkflow(id);
      setWorkflows((prev) => prev.filter((w) => w.id !== id));
    } catch (err) {
      console.error('Failed to delete workflow:', err);
    }
  };

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-6">
          <button
            onClick={() => setActiveTab('workflows')}
            className={`text-2xl font-bold pb-1 border-b-2 transition-colors ${
              activeTab === 'workflows' ? 'border-black text-black' : 'border-transparent text-gray-400 hover:text-gray-600'
            }`}
          >
            Workflows
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`text-2xl font-bold pb-1 border-b-2 transition-colors ${
              activeTab === 'history' ? 'border-black text-black' : 'border-transparent text-gray-400 hover:text-gray-600'
            }`}
          >
            History
          </button>
        </div>
        {activeTab === 'workflows' && <CreateWorkflowDialog onCreate={handleCreate} />}
      </div>

      {activeTab === 'workflows' && (
        <>
          {loading ? (
            <p className="text-gray-500">Loading workflows...</p>
          ) : workflows.length === 0 ? (
            <div className="text-center py-16">
              <p className="text-gray-500 text-lg">No workflows yet</p>
              <p className="text-gray-400 mt-1">Create your first workflow to get started</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {workflows.map((wf) => (
                <WorkflowCard key={wf.id} workflow={wf} onDelete={handleDelete} />
              ))}
            </div>
          )}
        </>
      )}

      {activeTab === 'history' && <ExecutionHistoryList />}
    </div>
  );
}
```

**Step 3: Commit**

```bash
git add frontend/app/page.tsx frontend/components/dashboard/execution-history-list.tsx
git commit -m "feat: add History tab to dashboard with execution list"
```

---

### Task 7: Frontend — History detail page with read-only canvas

**Files:**
- Create: `frontend/app/history/[executionId]/page.tsx`

**Step 1: Create the history detail page**

Create directory and file `frontend/app/history/[executionId]/page.tsx`:

```typescript
'use client';

import { use, useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'next/navigation';
import { api } from '@/lib/api';
import type { WorkflowDetail, ExecutionDetail, NodeExecutionResult } from '@/lib/types';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import {
  ReactFlow,
  Background,
  Controls,
  type Node,
  type Edge,
  type NodeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import Dagre from 'dagre';
import { StartFileUploadNode } from '@/components/canvas/nodes/start-file-upload-node';
import { StartQueryNode } from '@/components/canvas/nodes/start-query-node';
import { FilterNode } from '@/components/canvas/nodes/filter-node';
import { EnrichNode } from '@/components/canvas/nodes/enrich-node';
import { SplitNode } from '@/components/canvas/nodes/split-node';
import { JoinNode } from '@/components/canvas/nodes/join-node';
import { StopNode } from '@/components/canvas/nodes/stop-node';

const nodeTypes: NodeTypes = {
  START_FILE_UPLOAD: StartFileUploadNode,
  START_QUERY: StartQueryNode,
  FILTER: FilterNode,
  ENRICH: EnrichNode,
  SPLIT: SplitNode,
  JOIN: JoinNode,
  STOP: StopNode,
};

const statusColors: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  RUNNING: 'bg-blue-100 text-blue-700',
  SUCCESS: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
};

const statusBorderColors: Record<string, string> = {
  SUCCESS: '#22c55e',
  FAILED: '#ef4444',
  RUNNING: '#3b82f6',
  PENDING: '#9ca3af',
};

function getLayoutedElements(nodes: Node[], edges: Edge[]) {
  const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'TB', ranksep: 80, nodesep: 60 });
  nodes.forEach((node) => g.setNode(node.id, { width: 200, height: 80 }));
  edges.forEach((edge) => g.setEdge(edge.source, edge.target));
  Dagre.layout(g);
  return {
    nodes: nodes.map((node) => {
      const pos = g.node(node.id);
      return { ...node, position: { x: pos.x - 100, y: pos.y - 40 } };
    }),
    edges,
  };
}

export default function HistoryDetailPage({ params }: { params: Promise<{ executionId: string }> }) {
  const { executionId } = use(params);
  const searchParams = useSearchParams();
  const workflowId = searchParams.get('workflowId');

  const [workflow, setWorkflow] = useState<WorkflowDetail | null>(null);
  const [execution, setExecution] = useState<ExecutionDetail | null>(null);
  const [selectedNodeResult, setSelectedNodeResult] = useState<NodeExecutionResult | null>(null);
  const [resultRows, setResultRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!workflowId) return;
    Promise.all([
      api.getWorkflow(workflowId),
      api.getExecution(workflowId, executionId),
    ]).then(([wf, exec]) => {
      setWorkflow(wf);
      setExecution(exec);
    }).catch(console.error)
      .finally(() => setLoading(false));
  }, [workflowId, executionId]);

  const handleNodeClick = useCallback(async (_: React.MouseEvent, node: Node) => {
    if (!execution || !workflowId) return;
    const nr = execution.nodeResults.find((r) => r.nodeId === node.id);
    setSelectedNodeResult(nr || null);
    if (nr && nr.status === 'SUCCESS') {
      try {
        const rows = await api.getNodeResults(workflowId, executionId, nr.nodeId);
        setResultRows(rows);
      } catch {
        setResultRows([]);
      }
    } else {
      setResultRows([]);
    }
  }, [execution, workflowId, executionId]);

  if (loading) return <div className="p-6 text-gray-500">Loading execution details...</div>;
  if (!workflow || !execution) return <div className="p-6 text-red-500">Execution not found</div>;

  // Build read-only React Flow nodes with status colors
  const nodeResultMap = new Map(execution.nodeResults.map((nr) => [nr.nodeId, nr]));

  const rfNodes: Node[] = workflow.nodes.map((n) => {
    const nr = nodeResultMap.get(n.id);
    const borderColor = nr ? statusBorderColors[nr.status] || '#9ca3af' : '#9ca3af';
    return {
      id: n.id,
      type: n.type,
      position: { x: 0, y: 0 },
      data: {
        ...n.config,
        nodeType: n.type,
        nodeId: n.id,
        executionStatus: nr?.status,
        inputRecordCount: nr?.inputRecordCount,
        outputRecordCount: nr?.outputRecordCount,
      },
      style: { border: `3px solid ${borderColor}`, borderRadius: '8px' },
    };
  });

  const rfEdges: Edge[] = workflow.nodes.flatMap((n) =>
    n.parentNodeIds.map((parentId) => ({
      id: `${parentId}-${n.id}`,
      source: parentId,
      target: n.id,
      animated: false,
      style: { stroke: '#64748b', strokeWidth: 2 },
    }))
  );

  const { nodes: layoutedNodes, edges: layoutedEdges } = getLayoutedElements(rfNodes, rfEdges);

  return (
    <div className="flex flex-col h-[calc(100vh-56px)]">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-4 py-2 border-b bg-white">
        <div className="flex items-center gap-3">
          <Link href="/">
            <Button variant="ghost" size="sm">
              <ArrowLeft className="h-4 w-4" />
            </Button>
          </Link>
          <h1 className="font-semibold">{workflow.name}</h1>
          <Badge className={statusColors[execution.status] || 'bg-gray-100'} variant="secondary">
            {execution.status}
          </Badge>
          <span className="text-xs text-gray-400">
            {new Date(execution.startedAt).toLocaleString()}
          </span>
        </div>
      </div>

      {/* Canvas + Detail Panel */}
      <div className="flex flex-1 overflow-hidden">
        {/* Read-only canvas */}
        <div className="flex-1">
          <ReactFlow
            nodes={layoutedNodes}
            edges={layoutedEdges}
            onNodeClick={handleNodeClick}
            nodeTypes={nodeTypes}
            fitView
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable={true}
            proOptions={{ hideAttribution: true }}
          >
            <Background />
            <Controls showInteractive={false} />
          </ReactFlow>
        </div>

        {/* Node detail panel */}
        {selectedNodeResult && (
          <div className="w-[350px] border-l bg-white overflow-y-auto p-4">
            <h3 className="font-semibold text-sm mb-3">Node Details</h3>
            <div className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Node ID</span>
                <span className="font-mono text-xs">{selectedNodeResult.nodeId.slice(0, 8)}...</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Type</span>
                <span>{selectedNodeResult.nodeType}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Status</span>
                <Badge className={statusColors[selectedNodeResult.status]} variant="secondary">
                  {selectedNodeResult.status}
                </Badge>
              </div>
              {selectedNodeResult.inputRecordCount != null && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Input Records</span>
                  <span>{selectedNodeResult.inputRecordCount.toLocaleString()}</span>
                </div>
              )}
              {selectedNodeResult.filteredRecordCount != null && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Filtered Records</span>
                  <span>{selectedNodeResult.filteredRecordCount.toLocaleString()}</span>
                </div>
              )}
              {selectedNodeResult.outputRecordCount != null && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Output Records</span>
                  <span>{selectedNodeResult.outputRecordCount.toLocaleString()}</span>
                </div>
              )}
              {selectedNodeResult.errorMessage && (
                <div className="mt-2 p-2 bg-red-50 rounded text-red-700 text-xs">
                  {selectedNodeResult.errorMessage}
                </div>
              )}

              {/* Sample data */}
              {resultRows.length > 0 && (
                <div className="mt-4">
                  <h4 className="font-medium text-xs mb-2">Sample Data ({resultRows.length} rows)</h4>
                  <div className="overflow-auto max-h-[300px] border rounded">
                    <table className="text-xs w-full">
                      <thead className="bg-gray-50 sticky top-0">
                        <tr>
                          {Object.keys(resultRows[0]).map((key) => (
                            <th key={key} className="px-2 py-1 text-left font-medium text-gray-600 whitespace-nowrap">{key}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {resultRows.slice(0, 20).map((row, i) => (
                          <tr key={i} className="border-t">
                            {Object.values(row).map((val, j) => (
                              <td key={j} className="px-2 py-1 whitespace-nowrap">{val != null ? String(val) : ''}</td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
```

**Step 2: Commit**

```bash
git add frontend/app/history/[executionId]/page.tsx
git commit -m "feat: add history detail page with read-only workflow canvas"
```

---

### Task 8: Frontend — Data mart column dropdowns + SQL preview

**Files:**
- Modify: `frontend/components/panel/condition-builder.tsx`
- Modify: `frontend/components/panel/filter-config.tsx`
- Modify: `frontend/components/panel/enrich-config.tsx`
- Modify: `frontend/components/panel/query-config.tsx`

**Step 1: Update ConditionBuilder to accept columns prop and render dropdown**

Replace `frontend/components/panel/condition-builder.tsx`:

```typescript
'use client';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Trash2, PlusCircle } from 'lucide-react';

const OPERATORS = ['=', '!=', '>', '<', '>=', '<=', 'LIKE', 'NOT LIKE', 'IN', 'NOT IN', 'BETWEEN', 'IS NULL', 'IS NOT NULL'];

export interface ColumnInfo {
  columnName: string;
  dataType: string;
}

interface ConditionBuilderProps {
  conditions: Record<string, unknown>;
  onChange: (conditions: Record<string, unknown>) => void;
  columns?: ColumnInfo[];
  depth?: number;
}

export function ConditionBuilder({ conditions, onChange, columns, depth = 0 }: ConditionBuilderProps) {
  const operation = String(conditions.operation || 'AND');
  const items = (conditions.conditions as Array<Record<string, unknown>>) || [];

  const updateItem = (index: number, updated: Record<string, unknown>) => {
    const newItems = [...items];
    newItems[index] = updated;
    onChange({ ...conditions, conditions: newItems });
  };

  const removeItem = (index: number) => {
    onChange({ ...conditions, conditions: items.filter((_, i) => i !== index) });
  };

  const addCondition = () => {
    onChange({
      ...conditions,
      conditions: [...items, { field: '', operator: '=', value: '' }],
    });
  };

  const addGroup = () => {
    if (depth >= 2) return;
    onChange({
      ...conditions,
      conditions: [...items, { operation: 'AND', conditions: [] }],
    });
  };

  const toggleOperation = () => {
    onChange({ ...conditions, operation: operation === 'AND' ? 'OR' : 'AND' });
  };

  return (
    <div className={`space-y-2 ${depth > 0 ? 'border-l-2 border-gray-200 pl-3 ml-1' : ''}`}>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={toggleOperation} className="text-xs h-6 px-2">
          {operation}
        </Button>
        <span className="text-xs text-gray-400">group</span>
      </div>

      {items.map((item, index) => (
        <div key={index}>
          {isGroup(item) ? (
            <ConditionBuilder
              conditions={item}
              onChange={(updated) => updateItem(index, updated)}
              columns={columns}
              depth={depth + 1}
            />
          ) : (
            <ConditionRow
              condition={item}
              onChange={(updated) => updateItem(index, updated)}
              onRemove={() => removeItem(index)}
              columns={columns}
            />
          )}
        </div>
      ))}

      <div className="flex gap-2">
        <Button variant="ghost" size="sm" onClick={addCondition} className="text-xs h-7">
          <PlusCircle className="h-3 w-3 mr-1" />
          Condition
        </Button>
        {depth < 2 && (
          <Button variant="ghost" size="sm" onClick={addGroup} className="text-xs h-7">
            <PlusCircle className="h-3 w-3 mr-1" />
            Group
          </Button>
        )}
      </div>
    </div>
  );
}

function isGroup(item: Record<string, unknown>): boolean {
  return Array.isArray(item.conditions);
}

interface ConditionRowProps {
  condition: Record<string, unknown>;
  onChange: (condition: Record<string, unknown>) => void;
  onRemove: () => void;
  columns?: ColumnInfo[];
}

function ConditionRow({ condition, onChange, onRemove, columns }: ConditionRowProps) {
  const operator = String(condition.operator || '=');
  const isNullOp = operator === 'IS NULL' || operator === 'IS NOT NULL';

  return (
    <div className="flex items-center gap-1.5">
      {columns && columns.length > 0 ? (
        <select
          className="border rounded px-1.5 py-1 text-xs h-7 w-32"
          value={String(condition.field || '')}
          onChange={(e) => onChange({ ...condition, field: e.target.value })}
        >
          <option value="">Select field...</option>
          {columns.map((col) => (
            <option key={col.columnName} value={col.columnName}>
              {col.columnName} ({col.dataType})
            </option>
          ))}
        </select>
      ) : (
        <Input
          value={String(condition.field || '')}
          onChange={(e) => onChange({ ...condition, field: e.target.value })}
          placeholder="field"
          className="text-xs h-7 w-24"
        />
      )}
      <select
        className="border rounded px-1.5 py-1 text-xs h-7"
        value={operator}
        onChange={(e) => onChange({ ...condition, operator: e.target.value })}
      >
        {OPERATORS.map((op) => (
          <option key={op} value={op}>{op}</option>
        ))}
      </select>
      {!isNullOp && (
        <Input
          value={String(condition.value || '')}
          onChange={(e) => onChange({ ...condition, value: e.target.value })}
          placeholder="value"
          className="text-xs h-7 flex-1"
        />
      )}
      <Button variant="ghost" size="sm" onClick={onRemove} className="h-7 w-7 p-0">
        <Trash2 className="h-3 w-3 text-red-400" />
      </Button>
    </div>
  );
}
```

**Step 2: Update FilterConfig with column dropdown + SQL preview**

Replace `frontend/components/panel/filter-config.tsx`:

```typescript
'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { DataMart, DataMartColumn } from '@/lib/types';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { ConditionBuilder, type ColumnInfo } from './condition-builder';
import { SqlPreviewDialog } from './sql-preview-dialog';

interface FilterConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
  workflowId: string;
  nodeId: string;
}

export function FilterConfig({ config, onUpdate, workflowId, nodeId }: FilterConfigProps) {
  const [dataMarts, setDataMarts] = useState<DataMart[]>([]);
  const [selectedTable, setSelectedTable] = useState(String(config.data_mart_table || ''));
  const [mode, setMode] = useState(String(config.mode || 'JOIN'));
  const [joinKey, setJoinKey] = useState(String(config.join_key || ''));
  const [conditions, setConditions] = useState<Record<string, unknown>>(
    (config.conditions as Record<string, unknown>) || { operation: 'AND', conditions: [] }
  );
  const [columns, setColumns] = useState<ColumnInfo[]>([]);
  const [showSqlPreview, setShowSqlPreview] = useState(false);

  useEffect(() => {
    api.listDataMarts().then(setDataMarts).catch(console.error);
  }, []);

  // Fetch columns when data mart table changes
  useEffect(() => {
    if (selectedTable) {
      const dm = dataMarts.find((d) => d.tableName === selectedTable);
      if (dm) {
        api.getDataMart(dm.id).then((detail) => {
          setColumns(detail.columns.map((c: DataMartColumn) => ({ columnName: c.columnName, dataType: c.dataType })));
        }).catch(console.error);
      }
    } else {
      setColumns([]);
    }
  }, [selectedTable, dataMarts]);

  const currentConfig = {
    ...config,
    data_mart_table: selectedTable,
    mode,
    join_key: joinKey,
    conditions,
  };

  const handleSave = () => {
    onUpdate(currentConfig);
  };

  return (
    <div className="space-y-4">
      <div>
        <Label>Data Mart Table</Label>
        <select
          className="w-full border rounded-md px-3 py-2 text-sm mt-1"
          value={selectedTable}
          onChange={(e) => { setSelectedTable(e.target.value); }}
        >
          <option value="">Select a table...</option>
          {dataMarts.map((dm) => (
            <option key={dm.id} value={dm.tableName}>{dm.tableName} — {dm.description}</option>
          ))}
        </select>
      </div>

      <div>
        <Label>Mode</Label>
        <div className="flex gap-2 mt-1">
          <Button variant={mode === 'JOIN' ? 'default' : 'outline'} size="sm" onClick={() => setMode('JOIN')}>JOIN</Button>
          <Button variant={mode === 'SUBQUERY' ? 'default' : 'outline'} size="sm" onClick={() => setMode('SUBQUERY')}>Subquery</Button>
        </div>
      </div>

      <div>
        <Label htmlFor="joinKey">Join Key</Label>
        <Input id="joinKey" value={joinKey} onChange={(e) => setJoinKey(e.target.value)} placeholder="e.g., customer_id" />
      </div>

      <div>
        <Label>Conditions</Label>
        <ConditionBuilder
          conditions={conditions}
          onChange={setConditions}
          columns={columns}
        />
      </div>

      <div className="flex gap-2">
        <Button onClick={handleSave} className="flex-1">Save Configuration</Button>
        <Button variant="outline" onClick={() => setShowSqlPreview(true)}>Preview SQL</Button>
      </div>

      <SqlPreviewDialog
        open={showSqlPreview}
        onClose={() => setShowSqlPreview(false)}
        workflowId={workflowId}
        nodeId={nodeId}
        nodeType="FILTER"
        config={currentConfig}
      />
    </div>
  );
}
```

**Step 3: Update EnrichConfig with column dropdown + SQL preview**

Replace `frontend/components/panel/enrich-config.tsx`:

```typescript
'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { DataMart, DataMartColumn } from '@/lib/types';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { SqlPreviewDialog } from './sql-preview-dialog';

interface EnrichConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
  workflowId: string;
  nodeId: string;
}

export function EnrichConfig({ config, onUpdate, workflowId, nodeId }: EnrichConfigProps) {
  const [dataMarts, setDataMarts] = useState<DataMart[]>([]);
  const [selectedTable, setSelectedTable] = useState(String(config.data_mart_table || ''));
  const [mode, setMode] = useState(String(config.mode || 'ADD_COLUMNS'));
  const [joinKey, setJoinKey] = useState(String(config.join_key || ''));
  const [selectColumns, setSelectColumns] = useState<string[]>(
    (config.select_columns as string[]) || []
  );
  const [availableColumns, setAvailableColumns] = useState<{ columnName: string; dataType: string }[]>([]);
  const [showSqlPreview, setShowSqlPreview] = useState(false);

  useEffect(() => {
    api.listDataMarts().then(setDataMarts).catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedTable) {
      const dm = dataMarts.find((d) => d.tableName === selectedTable);
      if (dm) {
        api.getDataMart(dm.id).then((detail) => {
          setAvailableColumns(detail.columns.map((c: DataMartColumn) => ({ columnName: c.columnName, dataType: c.dataType })));
        }).catch(console.error);
      }
    } else {
      setAvailableColumns([]);
    }
  }, [selectedTable, dataMarts]);

  const currentConfig = {
    ...config,
    data_mart_table: selectedTable,
    mode,
    join_key: joinKey,
    select_columns: selectColumns,
  };

  const handleSave = () => {
    onUpdate(currentConfig);
  };

  const toggleColumn = (col: string) => {
    setSelectColumns((prev) =>
      prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col]
    );
  };

  return (
    <div className="space-y-4">
      <div>
        <Label>Data Mart Table</Label>
        <select
          className="w-full border rounded-md px-3 py-2 text-sm mt-1"
          value={selectedTable}
          onChange={(e) => setSelectedTable(e.target.value)}
        >
          <option value="">Select a table...</option>
          {dataMarts.map((dm) => (
            <option key={dm.id} value={dm.tableName}>{dm.tableName}</option>
          ))}
        </select>
      </div>

      <div>
        <Label>Mode</Label>
        <div className="flex gap-2 mt-1">
          <Button variant={mode === 'ADD_COLUMNS' ? 'default' : 'outline'} size="sm" onClick={() => setMode('ADD_COLUMNS')}>Add Columns</Button>
          <Button variant={mode === 'ADD_RECORDS' ? 'default' : 'outline'} size="sm" onClick={() => setMode('ADD_RECORDS')}>Add Records</Button>
        </div>
      </div>

      {mode === 'ADD_COLUMNS' && (
        <>
          <div>
            <Label htmlFor="enrichJoinKey">Join Key</Label>
            <Input id="enrichJoinKey" value={joinKey} onChange={(e) => setJoinKey(e.target.value)} placeholder="e.g., customer_id" />
          </div>
          <div>
            <Label>Columns to Add</Label>
            <div className="space-y-1 mt-1 max-h-40 overflow-y-auto">
              {availableColumns.map((col) => (
                <label key={col.columnName} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={selectColumns.includes(col.columnName)}
                    onChange={() => toggleColumn(col.columnName)}
                    className="rounded"
                  />
                  <span className="font-mono text-xs">{col.columnName}</span>
                  <span className="text-xs text-gray-400">({col.dataType})</span>
                </label>
              ))}
              {availableColumns.length === 0 && <p className="text-xs text-gray-400">Select a table first</p>}
            </div>
          </div>
        </>
      )}

      {mode === 'ADD_RECORDS' && (
        <p className="text-xs text-gray-400">Records from the data mart table will be added via UNION ALL. Column names must match.</p>
      )}

      <div className="flex gap-2">
        <Button onClick={handleSave} className="flex-1">Save Configuration</Button>
        <Button variant="outline" onClick={() => setShowSqlPreview(true)}>Preview SQL</Button>
      </div>

      <SqlPreviewDialog
        open={showSqlPreview}
        onClose={() => setShowSqlPreview(false)}
        workflowId={workflowId}
        nodeId={nodeId}
        nodeType="ENRICH"
        config={currentConfig}
      />
    </div>
  );
}
```

**Step 4: Update QueryConfig with SQL preview button**

Replace `frontend/components/panel/query-config.tsx`:

```typescript
'use client';

import { useState } from 'react';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { SqlPreviewDialog } from './sql-preview-dialog';

interface QueryConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
  workflowId: string;
  nodeId: string;
}

export function QueryConfig({ config, onUpdate, workflowId, nodeId }: QueryConfigProps) {
  const [sql, setSql] = useState(String(config.raw_sql || ''));
  const [showSqlPreview, setShowSqlPreview] = useState(false);

  const handleBlur = () => {
    onUpdate({ ...config, raw_sql: sql });
  };

  const currentConfig = { ...config, raw_sql: sql };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="sql">SQL Query</Label>
        <Textarea
          id="sql"
          value={sql}
          onChange={(e) => setSql(e.target.value)}
          onBlur={handleBlur}
          placeholder="SELECT * FROM customers WHERE ..."
          className="font-mono text-sm min-h-[120px]"
        />
        <p className="text-xs text-gray-400 mt-1">Enter a SQL SELECT query</p>
      </div>
      <Button variant="outline" onClick={() => setShowSqlPreview(true)} className="w-full">
        Preview SQL
      </Button>

      <SqlPreviewDialog
        open={showSqlPreview}
        onClose={() => setShowSqlPreview(false)}
        workflowId={workflowId}
        nodeId={nodeId}
        nodeType="START_QUERY"
        config={currentConfig}
      />
    </div>
  );
}
```

**Step 5: Create SqlPreviewDialog component**

Create `frontend/components/panel/sql-preview-dialog.tsx`:

```typescript
'use client';

import { useState } from 'react';
import { api } from '@/lib/api';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Loader2 } from 'lucide-react';

interface SqlPreviewDialogProps {
  open: boolean;
  onClose: () => void;
  workflowId: string;
  nodeId: string;
  nodeType: string;
  config: Record<string, unknown>;
}

export function SqlPreviewDialog({ open, onClose, workflowId, nodeId, nodeType, config }: SqlPreviewDialogProps) {
  const [sql, setSql] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchPreview = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.sqlPreview(workflowId, nodeId, { nodeType, config });
      setSql(result.sql);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate SQL preview');
    } finally {
      setLoading(false);
    }
  };

  // Fetch when dialog opens
  const handleOpenChange = (isOpen: boolean) => {
    if (isOpen) {
      fetchPreview();
    } else {
      setSql(null);
      setError(null);
      onClose();
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>SQL Preview</DialogTitle>
        </DialogHeader>
        <div className="mt-2">
          {loading && (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            </div>
          )}
          {error && (
            <div className="p-3 bg-red-50 rounded text-red-700 text-sm">{error}</div>
          )}
          {sql && (
            <pre className="bg-gray-900 text-green-400 p-4 rounded-lg text-sm overflow-auto max-h-[400px] font-mono whitespace-pre-wrap">
              {sql}
            </pre>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

**Step 6: Commit**

```bash
git add frontend/components/panel/condition-builder.tsx \
      frontend/components/panel/filter-config.tsx \
      frontend/components/panel/enrich-config.tsx \
      frontend/components/panel/query-config.tsx \
      frontend/components/panel/sql-preview-dialog.tsx
git commit -m "feat: add data mart column dropdowns and SQL preview dialog"
```

---

### Task 9: Verify full build

**Step 1: Build backend**

Run: `cd /Users/tushar/Desktop/Segment/segment && ./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 2: Build frontend**

Run: `cd /Users/tushar/Desktop/Segment/frontend && npm run build`
Expected: Build completes without errors

**Step 3: Fix any build errors**

If there are TypeScript or Java compilation errors, fix them before proceeding.

**Step 4: Commit any fixes**

```bash
git add -A && git commit -m "fix: resolve build errors from workflow enhancements"
```
