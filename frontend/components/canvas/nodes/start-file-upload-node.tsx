'use client';

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Upload } from 'lucide-react';

function StartFileUploadNodeComponent({ data }: NodeProps) {
  const d = data as Record<string, unknown>;
  return (
    <div className="bg-white border-2 border-blue-300 rounded-lg shadow-sm px-4 py-3 min-w-[180px]">
      <div className="flex items-center gap-2 mb-1">
        <Upload className="h-4 w-4 text-blue-500" />
        <span className="text-sm font-medium">File Upload</span>
      </div>
      {d.file_path ? <p className="text-xs text-gray-400 truncate">{String(d.file_path)}</p> : null}
      <Handle type="source" position={Position.Bottom} className="!bg-blue-400" />
    </div>
  );
}

export const StartFileUploadNode = memo(StartFileUploadNodeComponent);
