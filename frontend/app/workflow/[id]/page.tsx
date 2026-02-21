'use client';

import { use, useState, useEffect, useCallback } from 'react';
import { api } from '@/lib/api';
import type { WorkflowDetail, NodeType } from '@/lib/types';
import { WorkflowCanvas } from '@/components/canvas/workflow-canvas';
import { NodePalette } from '@/components/canvas/node-palette';
import { SidePanel } from '@/components/panel/side-panel';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Play } from 'lucide-react';
import Link from 'next/link';

export default function WorkflowEditorPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [workflow, setWorkflow] = useState<WorkflowDetail | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

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
    if (!workflow) return;
    try {
      const exec = await api.executeWorkflow(workflow.id);
      console.log('Execution started:', exec);
    } catch (err) {
      console.error('Failed to execute:', err);
    }
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
        <Button onClick={handleExecute} size="sm">
          <Play className="h-4 w-4 mr-1" />
          Execute All
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
            onPreview={(nId) => { console.log('Preview:', nId); }}
          />
        )}
      </div>
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
