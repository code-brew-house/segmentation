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
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
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
    setSelectedNodeId(node.id);
    const nr = execution.nodeResults.find((r) => r.nodeId === node.id) ?? null;
    setSelectedNodeResult(nr);
    if (nr?.status === 'SUCCESS') {
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
        {selectedNodeId && (() => {
          const nodeInfo = workflow.nodes.find((n) => n.id === selectedNodeId);
          const status = selectedNodeResult?.status ?? 'NOT STARTED';
          return (
            <div className="w-[350px] border-l bg-white overflow-y-auto p-4">
              <h3 className="font-semibold text-sm mb-3">Node Details</h3>
              <div className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">Node ID</span>
                  <span className="font-mono text-xs">{selectedNodeId.slice(0, 8)}...</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Type</span>
                  <span>{selectedNodeResult?.nodeType ?? nodeInfo?.type ?? '—'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Status</span>
                  <Badge className={statusColors[status] ?? 'bg-gray-100 text-gray-700'} variant="secondary">
                    {status}
                  </Badge>
                </div>
                {selectedNodeResult?.inputRecordCount != null && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Input Records</span>
                    <span>{selectedNodeResult.inputRecordCount.toLocaleString()}</span>
                  </div>
                )}
                {selectedNodeResult?.filteredRecordCount != null && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Filtered Records</span>
                    <span>{selectedNodeResult.filteredRecordCount.toLocaleString()}</span>
                  </div>
                )}
                {selectedNodeResult?.outputRecordCount != null && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Output Records</span>
                    <span>{selectedNodeResult.outputRecordCount.toLocaleString()}</span>
                  </div>
                )}
                {selectedNodeResult?.errorMessage && (
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
          );
        })()}
      </div>
    </div>
  );
}
