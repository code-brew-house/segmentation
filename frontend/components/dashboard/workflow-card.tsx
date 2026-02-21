'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Trash2, ExternalLink } from 'lucide-react';
import type { Workflow } from '@/lib/types';
import Link from 'next/link';

const statusColors: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700',
  RUNNING: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
};

interface WorkflowCardProps {
  workflow: Workflow;
  onDelete: (id: string) => void;
}

export function WorkflowCard({ workflow, onDelete }: WorkflowCardProps) {
  return (
    <Card className="hover:shadow-md transition-shadow">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-base font-medium truncate">{workflow.name}</CardTitle>
        <Badge className={statusColors[workflow.status] || 'bg-gray-100'} variant="secondary">
          {workflow.status}
        </Badge>
      </CardHeader>
      <CardContent>
        <div className="text-sm text-gray-500 space-y-1">
          {workflow.createdBy && <p>Created by: {workflow.createdBy}</p>}
          <p>Created: {new Date(workflow.createdAt).toLocaleDateString()}</p>
          <p>{workflow.nodeCount} node{workflow.nodeCount !== 1 ? 's' : ''}</p>
        </div>
        <div className="flex gap-2 mt-4">
          <Link href={`/workflow/${workflow.id}`} className="flex-1">
            <Button variant="default" size="sm" className="w-full">
              <ExternalLink className="h-4 w-4 mr-1" />
              Open
            </Button>
          </Link>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              if (confirm('Delete this workflow?')) onDelete(workflow.id);
            }}
          >
            <Trash2 className="h-4 w-4 text-red-500" />
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
