'use client';

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { GitFork } from 'lucide-react';

function SplitNodeComponent(_props: NodeProps) {
  return (
    <div className="bg-white border-2 border-purple-300 rounded-lg shadow-sm px-4 py-3 min-w-[120px] text-center">
      <Handle type="target" position={Position.Top} className="!bg-purple-400" />
      <div className="flex items-center justify-center gap-2">
        <GitFork className="h-4 w-4 text-purple-500" />
        <span className="text-sm font-medium">Split</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-purple-400" />
    </div>
  );
}

export const SplitNode = memo(SplitNodeComponent);
