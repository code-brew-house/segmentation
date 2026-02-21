'use client';

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Database } from 'lucide-react';

function StartQueryNodeComponent({ data }: NodeProps) {
  const d = data as Record<string, unknown>;
  const rawSql = d.raw_sql ? String(d.raw_sql) : '';
  const sqlPreview = rawSql ? rawSql.substring(0, 40) + '...' : '';
  return (
    <div className="bg-white border-2 border-blue-300 rounded-lg shadow-sm px-4 py-3 min-w-[180px]">
      <div className="flex items-center gap-2 mb-1">
        <Database className="h-4 w-4 text-blue-500" />
        <span className="text-sm font-medium">Query</span>
      </div>
      {sqlPreview ? <p className="text-xs text-gray-400 truncate font-mono">{sqlPreview}</p> : null}
      <Handle type="source" position={Position.Bottom} className="!bg-blue-400" />
    </div>
  );
}

export const StartQueryNode = memo(StartQueryNodeComponent);
