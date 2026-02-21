'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { DataMart, DataMartColumn } from '@/lib/types';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { SqlPreviewDialog } from './sql-preview-dialog';

interface EnrichConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
  workflowId?: string;
  nodeId?: string;
}

export function EnrichConfig({ config, onUpdate, workflowId, nodeId }: EnrichConfigProps) {
  const [dataMarts, setDataMarts] = useState<DataMart[]>([]);
  const [selectedTable, setSelectedTable] = useState(String(config.data_mart_table || ''));
  const [mode, setMode] = useState(String(config.mode || 'ADD_COLUMNS'));
  const [joinKey, setJoinKey] = useState(String(config.join_key || ''));
  const [selectColumns, setSelectColumns] = useState<string[]>(
    (config.select_columns as string[]) || []
  );
  const [availableColumns, setAvailableColumns] = useState<{ columnName: string; dataType: string }[]>([]);
  const [showSqlPreview, setShowSqlPreview] = useState(false);

  useEffect(() => {
    api.listDataMarts().then(setDataMarts).catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedTable) {
      const dm = dataMarts.find((d) => d.tableName === selectedTable);
      if (dm) {
        api.getDataMart(dm.id).then((detail) => {
          setAvailableColumns(detail.columns.map((c: DataMartColumn) => ({ columnName: c.columnName, dataType: c.dataType })));
        }).catch(console.error);
      }
    } else {
      setAvailableColumns([]);
    }
  }, [selectedTable, dataMarts]);

  const currentConfig = {
    ...config,
    data_mart_table: selectedTable,
    mode,
    join_key: joinKey,
    select_columns: selectColumns,
  };

  const handleSave = () => {
    onUpdate(currentConfig);
  };

  const toggleColumn = (col: string) => {
    setSelectColumns((prev) =>
      prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col]
    );
  };

  return (
    <div className="space-y-4">
      <div>
        <Label>Data Mart Table</Label>
        <select
          className="w-full border rounded-md px-3 py-2 text-sm mt-1"
          value={selectedTable}
          onChange={(e) => setSelectedTable(e.target.value)}
        >
          <option value="">Select a table...</option>
          {dataMarts.map((dm) => (
            <option key={dm.id} value={dm.tableName}>{dm.tableName}</option>
          ))}
        </select>
      </div>

      <div>
        <Label>Mode</Label>
        <div className="flex gap-2 mt-1">
          <Button variant={mode === 'ADD_COLUMNS' ? 'default' : 'outline'} size="sm" onClick={() => setMode('ADD_COLUMNS')}>Add Columns</Button>
          <Button variant={mode === 'ADD_RECORDS' ? 'default' : 'outline'} size="sm" onClick={() => setMode('ADD_RECORDS')}>Add Records</Button>
        </div>
      </div>

      {mode === 'ADD_COLUMNS' && (
        <>
          <div>
            <Label htmlFor="enrichJoinKey">Join Key</Label>
            <Input id="enrichJoinKey" value={joinKey} onChange={(e) => setJoinKey(e.target.value)} placeholder="e.g., customer_id" />
          </div>
          <div>
            <Label>Columns to Add</Label>
            <div className="space-y-1 mt-1 max-h-40 overflow-y-auto">
              {availableColumns.map((col) => (
                <label key={col.columnName} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={selectColumns.includes(col.columnName)}
                    onChange={() => toggleColumn(col.columnName)}
                    className="rounded"
                  />
                  <span className="font-mono text-xs">{col.columnName}</span>
                  <span className="text-xs text-gray-400">({col.dataType})</span>
                </label>
              ))}
              {availableColumns.length === 0 && <p className="text-xs text-gray-400">Select a table first</p>}
            </div>
          </div>
        </>
      )}

      {mode === 'ADD_RECORDS' && (
        <p className="text-xs text-gray-400">Records from the data mart table will be added via UNION ALL. Column names must match.</p>
      )}

      <div className="flex gap-2">
        <Button onClick={handleSave} className="flex-1">Save Configuration</Button>
        {workflowId && nodeId && (
          <Button variant="outline" onClick={() => setShowSqlPreview(true)}>Preview SQL</Button>
        )}
      </div>

      {workflowId && nodeId && (
        <SqlPreviewDialog
          open={showSqlPreview}
          onClose={() => setShowSqlPreview(false)}
          workflowId={workflowId}
          nodeId={nodeId}
          nodeType="ENRICH"
          config={currentConfig}
        />
      )}
    </div>
  );
}
