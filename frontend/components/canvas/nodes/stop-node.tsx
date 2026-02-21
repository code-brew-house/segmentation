'use client';

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Download } from 'lucide-react';

function StopNodeComponent({ data }: NodeProps) {
  const d = data as Record<string, unknown>;
  return (
    <div className="bg-white border-2 border-red-300 rounded-lg shadow-sm px-4 py-3 min-w-[180px]">
      <Handle type="target" position={Position.Top} className="!bg-red-400" />
      <div className="flex items-center gap-2 mb-1">
        <Download className="h-4 w-4 text-red-500" />
        <span className="text-sm font-medium">Stop</span>
      </div>
      {d.output_file_path ? <p className="text-xs text-gray-400 truncate">{String(d.output_file_path)}</p> : null}
    </div>
  );
}

export const StopNode = memo(StopNodeComponent);
