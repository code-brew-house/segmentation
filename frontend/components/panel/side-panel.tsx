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
