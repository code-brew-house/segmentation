'use client';

import { useState } from 'react';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

interface QueryConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
  workflowId?: string;
  nodeId?: string;
}

export function QueryConfig({ config, onUpdate }: QueryConfigProps) {
  const [sql, setSql] = useState(String(config.raw_sql || ''));

  const handleBlur = () => {
    onUpdate({ ...config, raw_sql: sql });
  };

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
    </div>
  );
}
