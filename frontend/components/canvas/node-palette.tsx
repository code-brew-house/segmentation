'use client';

import { Button } from '@/components/ui/button';
import { Upload, Database, Filter, Plus, GitFork, Merge, Download } from 'lucide-react';
import type { NodeType } from '@/lib/types';

interface NodePaletteProps {
  onAddNode: (type: NodeType) => void;
}

const nodeGroups = [
  {
    label: 'Start',
    items: [
      { type: 'START_FILE_UPLOAD' as NodeType, label: 'File Upload', icon: Upload },
      { type: 'START_QUERY' as NodeType, label: 'Query', icon: Database },
    ],
  },
  {
    label: 'Transform',
    items: [
      { type: 'FILTER' as NodeType, label: 'Filter', icon: Filter },
      { type: 'ENRICH' as NodeType, label: 'Enrich', icon: Plus },
    ],
  },
  {
    label: 'Flow',
    items: [
      { type: 'SPLIT' as NodeType, label: 'Split', icon: GitFork },
      { type: 'JOIN' as NodeType, label: 'Join', icon: Merge },
    ],
  },
  {
    label: 'Terminal',
    items: [
      { type: 'STOP' as NodeType, label: 'Stop', icon: Download },
    ],
  },
];

export function NodePalette({ onAddNode }: NodePaletteProps) {
  return (
    <div className="w-48 bg-white border-r p-3 space-y-4 overflow-y-auto">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Nodes</p>
      {nodeGroups.map((group) => (
        <div key={group.label}>
          <p className="text-xs text-gray-400 mb-1">{group.label}</p>
          <div className="space-y-1">
            {group.items.map((item) => (
              <Button
                key={item.type}
                variant="ghost"
                size="sm"
                className="w-full justify-start text-xs"
                onClick={() => onAddNode(item.type)}
              >
                <item.icon className="h-3.5 w-3.5 mr-2" />
                {item.label}
              </Button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
