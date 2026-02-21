'use client';

import { use, useState, useEffect, useCallback, useRef } from 'react';
import { api } from '@/lib/api';
import type { WorkflowDetail, NodeType, ExecutionDetail, NodeExecutionResult } from '@/lib/types';
import { WorkflowCanvas } from '@/components/canvas/workflow-canvas';
import { NodePalette } from '@/components/canvas/node-palette';
import { SidePanel } from '@/components/panel/side-panel';
import { ExecutionConsole, type LogEntry, type ConsoleTab } from '@/components/console/execution-console';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Play, Loader2 } from 'lucide-react';
import Link from 'next/link';

export default function WorkflowEditorPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [workflow, setWorkflow] = useState<WorkflowDetail | null>(null);
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
    } catch (err) {
      console.error('Failed to load workflow:', err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { loadWorkflow(); }, [loadWorkflow]);

  // Cleanup polling on unmount
  useEffect(() => {
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
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

  const handleAddNode = async (type: NodeType) => {
    if (!workflow) return;
    try {
      await api.addNode(workflow.id, {
        parentNodeIds: [],
        type,
        config: getDefaultConfig(type),
      });
      await loadWorkflow();
    } catch (err) {
      console.error('Failed to add node:', err);
    }
  };

  const handleDeleteNode = async (nodeId: string) => {
    if (!workflow) return;
    try {
      await api.deleteNode(workflow.id, nodeId);
      setSelectedNodeId(null);
      await loadWorkflow();
    } catch (err) {
      console.error('Failed to delete node:', err);
    }
  };

  const handleConnect = async (sourceId: string, targetId: string) => {
    if (!workflow) return;
    const targetNode = workflow.nodes.find((n) => n.id === targetId);
    if (!targetNode) return;
    try {
      await api.updateNode(workflow.id, targetId, {
        parentNodeIds: [...targetNode.parentNodeIds, sourceId],
      });
      await loadWorkflow();
    } catch (err) {
      console.error('Failed to connect nodes:', err);
    }
  };

  const handleExecute = async () => {
    if (!workflow || isExecuting) return;
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
    try {
      setConsoleOpen(true);
      setConsoleTab('log');
      addLog(`Previewing node ${nodeId.slice(0, 8)}...`);
      setIsPreviewing(true);

      const preview = await api.previewNode(workflow.id, nodeId);

      setIsPreviewing(false);
      addLog(
        `Preview complete: ${preview.inputCount} input, ${preview.filteredCount} filtered, ${preview.outputCount} output`,
        'success',
      );
      setResultRows(preview.sampleRows);
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
        </div>
        <Button onClick={handleExecute} size="sm" disabled={isExecuting}>
          {isExecuting ? (
            <Loader2 className="h-4 w-4 mr-1 animate-spin" />
          ) : (
            <Play className="h-4 w-4 mr-1" />
          )}
          {isExecuting ? 'Executing...' : 'Execute All'}
        </Button>
      </div>

      {/* Canvas area */}
      <div className="flex flex-1 relative overflow-hidden">
        {/* Node Palette */}
        <NodePalette onAddNode={handleAddNode} />

        {/* React Flow Canvas */}
        <div className="flex-1">
          <WorkflowCanvas
            nodes={workflow.nodes}
            onNodeSelect={setSelectedNodeId}
            onConnect={handleConnect}
            onDeleteNode={handleDeleteNode}
            workflowId={workflow.id}
            onReload={loadWorkflow}
          />
        </div>

        {/* Side Panel */}
        {selectedNodeId && workflow && (
          <SidePanel
            workflowId={workflow.id}
            nodeId={selectedNodeId}
            nodes={workflow.nodes}
            onClose={() => setSelectedNodeId(null)}
            onDelete={handleDeleteNode}
            onReload={loadWorkflow}
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
