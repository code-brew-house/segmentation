'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { ExecutionHistoryItem } from '@/lib/types';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import Link from 'next/link';

const statusColors: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  RUNNING: 'bg-blue-100 text-blue-700',
  SUCCESS: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
};

const dotColors: Record<string, string> = {
  SUCCESS: 'bg-green-500',
  FAILED: 'bg-red-500',
  RUNNING: 'bg-blue-500',
  PENDING: 'bg-gray-300',
};

function formatDuration(startedAt: string, completedAt: string | null): string {
  if (!completedAt) return 'In progress...';
  const ms = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
}

export function ExecutionHistoryList() {
  const [executions, setExecutions] = useState<ExecutionHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.listAllExecutions()
      .then(setExecutions)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="text-gray-500">Loading execution history...</p>;

  if (executions.length === 0) {
    return (
      <div className="text-center py-16">
        <p className="text-gray-500 text-lg">No executions yet</p>
        <p className="text-gray-400 mt-1">Run a workflow to see execution history here</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {executions.map((exec) => (
        <Link key={exec.executionId} href={`/history/${exec.executionId}?workflowId=${exec.workflowId}`}>
          <Card className="hover:shadow-md transition-shadow cursor-pointer">
            <CardContent className="py-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div>
                    <p className="font-medium text-sm">{exec.workflowName}</p>
                    <p className="text-xs text-gray-400">
                      {new Date(exec.startedAt).toLocaleString()} · {formatDuration(exec.startedAt, exec.completedAt)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  {/* Node status dots */}
                  <div className="flex items-center gap-1">
                    {exec.passedNodes > 0 && (
                      <div className="flex items-center gap-0.5">
                        <span className={`w-2 h-2 rounded-full ${dotColors.SUCCESS}`} />
                        <span className="text-xs text-gray-500">{exec.passedNodes}</span>
                      </div>
                    )}
                    {exec.failedNodes > 0 && (
                      <div className="flex items-center gap-0.5">
                        <span className={`w-2 h-2 rounded-full ${dotColors.FAILED}`} />
                        <span className="text-xs text-gray-500">{exec.failedNodes}</span>
                      </div>
                    )}
                    {exec.totalNodes - exec.passedNodes - exec.failedNodes > 0 && (
                      <div className="flex items-center gap-0.5">
                        <span className={`w-2 h-2 rounded-full ${dotColors.PENDING}`} />
                        <span className="text-xs text-gray-500">{exec.totalNodes - exec.passedNodes - exec.failedNodes}</span>
                      </div>
                    )}
                  </div>
                  <Badge className={statusColors[exec.status] || 'bg-gray-100'} variant="secondary">
                    {exec.status}
                  </Badge>
                </div>
              </div>
            </CardContent>
          </Card>
        </Link>
      ))}
    </div>
  );
}
