'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { DataMart } from '@/lib/types';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { ConditionBuilder } from './condition-builder';

interface FilterConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
}

export function FilterConfig({ config, onUpdate }: FilterConfigProps) {
  const [dataMarts, setDataMarts] = useState<DataMart[]>([]);
  const [selectedTable, setSelectedTable] = useState(String(config.data_mart_table || ''));
  const [mode, setMode] = useState(String(config.mode || 'JOIN'));
  const [joinKey, setJoinKey] = useState(String(config.join_key || ''));
  const [conditions, setConditions] = useState<Record<string, unknown>>(
    (config.conditions as Record<string, unknown>) || { operation: 'AND', conditions: [] }
  );

  useEffect(() => {
    api.listDataMarts().then(setDataMarts).catch(console.error);
  }, []);

  const handleSave = () => {
    onUpdate({
      ...config,
      data_mart_table: selectedTable,
      mode,
      join_key: joinKey,
      conditions,
    });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label>Data Mart Table</Label>
        <select
          className="w-full border rounded-md px-3 py-2 text-sm mt-1"
          value={selectedTable}
          onChange={(e) => { setSelectedTable(e.target.value); }}
        >
          <option value="">Select a table...</option>
          {dataMarts.map((dm) => (
            <option key={dm.id} value={dm.tableName}>{dm.tableName} — {dm.description}</option>
          ))}
        </select>
      </div>

      <div>
        <Label>Mode</Label>
        <div className="flex gap-2 mt-1">
          <Button variant={mode === 'JOIN' ? 'default' : 'outline'} size="sm" onClick={() => setMode('JOIN')}>JOIN</Button>
          <Button variant={mode === 'SUBQUERY' ? 'default' : 'outline'} size="sm" onClick={() => setMode('SUBQUERY')}>Subquery</Button>
        </div>
      </div>

      <div>
        <Label htmlFor="joinKey">Join Key</Label>
        <Input id="joinKey" value={joinKey} onChange={(e) => setJoinKey(e.target.value)} placeholder="e.g., customer_id" />
      </div>

      <div>
        <Label>Conditions</Label>
        <ConditionBuilder
          conditions={conditions}
          onChange={setConditions}
        />
      </div>

      <Button onClick={handleSave} className="w-full">Save Configuration</Button>
    </div>
  );
}
