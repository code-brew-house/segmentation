'use client';

import type { NodeExecutionResult } from '@/lib/types';
import { Badge } from '@/components/ui/badge';
import { ResultTable } from './result-table';
import { MetricsTable } from './metrics-table';
import { ChevronDown, ChevronUp, Terminal, Loader2 } from 'lucide-react';

export interface LogEntry {
  timestamp: string;
  message: string;
  type: 'info' | 'error' | 'success';
}

export type ConsoleTab = 'log' | 'results' | 'metrics';

interface ExecutionConsoleProps {
  open: boolean;
  onToggle: () => void;
  logEntries: LogEntry[];
  resultRows: Record<string, unknown>[];
  nodeResults: NodeExecutionResult[];
  isRunning: boolean;
  activeTab: ConsoleTab;
  onTabChange: (tab: ConsoleTab) => void;
  onViewNodeResults: (nodeId: string) => void;
  onDownloadCsv: (nodeId: string) => void;
}

export function ExecutionConsole({
  open,
  onToggle,
  logEntries,
  resultRows,
  nodeResults,
  isRunning,
  activeTab,
  onTabChange,
  onViewNodeResults,
  onDownloadCsv,
}: ExecutionConsoleProps) {
  const tabs: { key: ConsoleTab; label: string }[] = [
    { key: 'log', label: 'Log' },
    { key: 'results', label: 'Results' },
    { key: 'metrics', label: 'Metrics' },
  ];

  return (
    <div className="border-t bg-white flex flex-col">
      {/* Header bar - always visible */}
      <button
        onClick={onToggle}
        className="flex items-center justify-between px-4 py-2 hover:bg-gray-50 cursor-pointer"
      >
        <div className="flex items-center gap-2">
          <Terminal className="h-4 w-4 text-gray-500" />
          <span className="text-sm font-medium">Console</span>
          {isRunning && <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-500" />}
          {logEntries.length > 0 && (
            <Badge variant="secondary" className="text-xs">
              {logEntries.length}
            </Badge>
          )}
        </div>
        {open ? <ChevronDown className="h-4 w-4 text-gray-400" /> : <ChevronUp className="h-4 w-4 text-gray-400" />}
      </button>

      {/* Expandable content */}
      {open && (
        <div className="flex flex-col h-[280px]">
          {/* Tabs */}
          <div className="flex items-center gap-1 px-4 border-b">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => onTabChange(tab.key)}
                className={`px-3 py-1.5 text-xs font-medium border-b-2 transition-colors ${
                  activeTab === tab.key
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="flex-1 overflow-auto">
            {activeTab === 'log' && (
              <div className="font-mono text-xs p-3 space-y-1">
                {logEntries.length === 0 && (
                  <p className="text-gray-400">No log entries yet. Execute a workflow or preview a node.</p>
                )}
                {logEntries.map((entry, i) => (
                  <div key={i} className="flex gap-3">
                    <span className="text-gray-400 shrink-0">{entry.timestamp}</span>
                    <span
                      className={
                        entry.type === 'error'
                          ? 'text-red-600'
                          : entry.type === 'success'
                            ? 'text-green-600'
                            : 'text-gray-700'
                      }
                    >
                      {entry.message}
                    </span>
                  </div>
                ))}
              </div>
            )}

            {activeTab === 'results' && (
              <ResultTable rows={resultRows} />
            )}

            {activeTab === 'metrics' && (
              <MetricsTable
                nodeResults={nodeResults}
                onViewResults={onViewNodeResults}
                onDownload={onDownloadCsv}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}
