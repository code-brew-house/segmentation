'use client';

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Filter } from 'lucide-react';

function FilterNodeComponent({ data }: NodeProps) {
  const d = data as Record<string, unknown>;
  const conditions = d.conditions as Record<string, unknown> | undefined;
  const condList = conditions?.conditions as unknown[] | undefined;
  const condCount = condList?.length || 0;
  return (
    <div className="bg-white border-2 border-amber-300 rounded-lg shadow-sm px-4 py-3 min-w-[180px]">
      <Handle type="target" position={Position.Top} className="!bg-amber-400" />
      <div className="flex items-center gap-2 mb-1">
        <Filter className="h-4 w-4 text-amber-500" />
        <span className="text-sm font-medium">Filter</span>
      </div>
      <p className="text-xs text-gray-400">
        {String(d.data_mart_table || 'No table')} ({String(d.mode || 'JOIN')}) {condCount > 0 ? `· ${condCount} conditions` : ''}
      </p>
      <Handle type="source" position={Position.Bottom} className="!bg-amber-400" />
    </div>
  );
}

export const FilterNode = memo(FilterNodeComponent);
