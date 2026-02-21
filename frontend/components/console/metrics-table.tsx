'use client';

import type { NodeExecutionResult } from '@/lib/types';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Download, Eye } from 'lucide-react';

interface MetricsTableProps {
  nodeResults: NodeExecutionResult[];
  onViewResults: (nodeId: string) => void;
  onDownload: (nodeId: string) => void;
}

function statusVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'SUCCESS': return 'default';
    case 'RUNNING': return 'secondary';
    case 'FAILED': return 'destructive';
    default: return 'outline';
  }
}

export function MetricsTable({ nodeResults, onViewResults, onDownload }: MetricsTableProps) {
  if (nodeResults.length === 0) {
    return <p className="text-sm text-gray-500 p-4">No execution metrics available.</p>;
  }

  return (
    <div className="overflow-auto max-h-[300px]">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Node</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Input Records</TableHead>
            <TableHead>Filtered</TableHead>
            <TableHead>Output Records</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {nodeResults.map((nr) => (
            <TableRow key={nr.nodeId}>
              <TableCell className="font-mono text-xs">{nr.nodeId.slice(0, 8)}</TableCell>
              <TableCell>{nr.nodeType}</TableCell>
              <TableCell>{nr.inputRecordCount ?? '-'}</TableCell>
              <TableCell>{nr.filteredRecordCount ?? '-'}</TableCell>
              <TableCell>{nr.outputRecordCount ?? '-'}</TableCell>
              <TableCell>
                <Badge variant={statusVariant(nr.status)}>{nr.status}</Badge>
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  {nr.status === 'SUCCESS' && (
                    <Button variant="ghost" size="sm" onClick={() => onViewResults(nr.nodeId)} title="View results">
                      <Eye className="h-3.5 w-3.5" />
                    </Button>
                  )}
                  {nr.nodeType === 'STOP' && nr.status === 'SUCCESS' && (
                    <Button variant="ghost" size="sm" onClick={() => onDownload(nr.nodeId)} title="Download CSV">
                      <Download className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
