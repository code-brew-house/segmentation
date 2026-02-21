'use client';

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Merge } from 'lucide-react';

function JoinNodeComponent(_props: NodeProps) {
  return (
    <div className="bg-white border-2 border-purple-300 rounded-lg shadow-sm px-4 py-3 min-w-[120px] text-center">
      <Handle type="target" position={Position.Top} className="!bg-purple-400" />
      <div className="flex items-center justify-center gap-2">
        <Merge className="h-4 w-4 text-purple-500" />
        <span className="text-sm font-medium">Join</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-purple-400" />
    </div>
  );
}

export const JoinNode = memo(JoinNodeComponent);
