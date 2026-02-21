'use client';

import { useState } from 'react';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { SqlPreviewDialog } from './sql-preview-dialog';

interface QueryConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
  workflowId?: string;
  nodeId?: string;
}

export function QueryConfig({ config, onUpdate, workflowId, nodeId }: QueryConfigProps) {
  const [sql, setSql] = useState(String(config.raw_sql || ''));
  const [showSqlPreview, setShowSqlPreview] = useState(false);

  const handleBlur = () => {
    onUpdate({ ...config, raw_sql: sql });
  };

  const currentConfig = { ...config, raw_sql: sql };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="sql">SQL Query</Label>
        <Textarea
          id="sql"
          value={sql}
          onChange={(e) => setSql(e.target.value)}
          onBlur={handleBlur}
          placeholder="SELECT * FROM customers WHERE ..."
          className="font-mono text-sm min-h-[120px]"
        />
        <p className="text-xs text-gray-400 mt-1">Enter a SQL SELECT query</p>
      </div>
      {workflowId && nodeId && (
        <>
          <Button variant="outline" onClick={() => setShowSqlPreview(true)} className="w-full">
            Preview SQL
          </Button>
          <SqlPreviewDialog
            open={showSqlPreview}
            onClose={() => setShowSqlPreview(false)}
            workflowId={workflowId}
            nodeId={nodeId}
            nodeType="START_QUERY"
            config={currentConfig}
          />
        </>
      )}
    </div>
  );
}
