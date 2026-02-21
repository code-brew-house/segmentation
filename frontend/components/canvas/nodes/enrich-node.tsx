'use client';

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Plus } from 'lucide-react';

function EnrichNodeComponent({ data }: NodeProps) {
  const d = data as Record<string, unknown>;
  const modeLabel = d.mode === 'ADD_RECORDS' ? 'Add Records' : 'Add Columns';
  return (
    <div className="bg-white border-2 border-green-300 rounded-lg shadow-sm px-4 py-3 min-w-[180px]">
      <Handle type="target" position={Position.Top} className="!bg-green-400" />
      <div className="flex items-center gap-2 mb-1">
        <Plus className="h-4 w-4 text-green-500" />
        <span className="text-sm font-medium">Enrich</span>
      </div>
      <p className="text-xs text-gray-400">{modeLabel} · {String(d.data_mart_table || 'No table')}</p>
      <Handle type="source" position={Position.Bottom} className="!bg-green-400" />
    </div>
  );
}

export const EnrichNode = memo(EnrichNodeComponent);
