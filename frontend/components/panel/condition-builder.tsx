'use client';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Trash2, PlusCircle } from 'lucide-react';

const OPERATORS = ['=', '!=', '>', '<', '>=', '<=', 'LIKE', 'NOT LIKE', 'IN', 'NOT IN', 'BETWEEN', 'IS NULL', 'IS NOT NULL'];

interface ConditionBuilderProps {
  conditions: Record<string, unknown>;
  onChange: (conditions: Record<string, unknown>) => void;
  depth?: number;
}

export function ConditionBuilder({ conditions, onChange, depth = 0 }: ConditionBuilderProps) {
  const operation = String(conditions.operation || 'AND');
  const items = (conditions.conditions as Array<Record<string, unknown>>) || [];

  const updateItem = (index: number, updated: Record<string, unknown>) => {
    const newItems = [...items];
    newItems[index] = updated;
    onChange({ ...conditions, conditions: newItems });
  };

  const removeItem = (index: number) => {
    onChange({ ...conditions, conditions: items.filter((_, i) => i !== index) });
  };

  const addCondition = () => {
    onChange({
      ...conditions,
      conditions: [...items, { field: '', operator: '=', value: '' }],
    });
  };

  const addGroup = () => {
    if (depth >= 2) return;
    onChange({
      ...conditions,
      conditions: [...items, { operation: 'AND', conditions: [] }],
    });
  };

  const toggleOperation = () => {
    onChange({ ...conditions, operation: operation === 'AND' ? 'OR' : 'AND' });
  };

  return (
    <div className={`space-y-2 ${depth > 0 ? 'border-l-2 border-gray-200 pl-3 ml-1' : ''}`}>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={toggleOperation} className="text-xs h-6 px-2">
          {operation}
        </Button>
        <span className="text-xs text-gray-400">group</span>
      </div>

      {items.map((item, index) => (
        <div key={index}>
          {isGroup(item) ? (
            <ConditionBuilder
              conditions={item}
              onChange={(updated) => updateItem(index, updated)}
              depth={depth + 1}
            />
          ) : (
            <ConditionRow
              condition={item}
              onChange={(updated) => updateItem(index, updated)}
              onRemove={() => removeItem(index)}
            />
          )}
        </div>
      ))}

      <div className="flex gap-2">
        <Button variant="ghost" size="sm" onClick={addCondition} className="text-xs h-7">
          <PlusCircle className="h-3 w-3 mr-1" />
          Condition
        </Button>
        {depth < 2 && (
          <Button variant="ghost" size="sm" onClick={addGroup} className="text-xs h-7">
            <PlusCircle className="h-3 w-3 mr-1" />
            Group
          </Button>
        )}
      </div>
    </div>
  );
}

function isGroup(item: Record<string, unknown>): boolean {
  return Array.isArray(item.conditions);
}

interface ConditionRowProps {
  condition: Record<string, unknown>;
  onChange: (condition: Record<string, unknown>) => void;
  onRemove: () => void;
}

function ConditionRow({ condition, onChange, onRemove }: ConditionRowProps) {
  const operator = String(condition.operator || '=');
  const isNullOp = operator === 'IS NULL' || operator === 'IS NOT NULL';

  return (
    <div className="flex items-center gap-1.5">
      <Input
        value={String(condition.field || '')}
        onChange={(e) => onChange({ ...condition, field: e.target.value })}
        placeholder="field"
        className="text-xs h-7 w-24"
      />
      <select
        className="border rounded px-1.5 py-1 text-xs h-7"
        value={operator}
        onChange={(e) => onChange({ ...condition, operator: e.target.value })}
      >
        {OPERATORS.map((op) => (
          <option key={op} value={op}>{op}</option>
        ))}
      </select>
      {!isNullOp && (
        <Input
          value={String(condition.value || '')}
          onChange={(e) => onChange({ ...condition, value: e.target.value })}
          placeholder="value"
          className="text-xs h-7 flex-1"
        />
      )}
      <Button variant="ghost" size="sm" onClick={onRemove} className="h-7 w-7 p-0">
        <Trash2 className="h-3 w-3 text-red-400" />
      </Button>
    </div>
  );
}
