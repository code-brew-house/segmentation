'use client';

import { useState } from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

interface StopConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
}

export function StopConfig({ config, onUpdate }: StopConfigProps) {
  const [filePath, setFilePath] = useState(String(config.output_file_path || ''));

  const handleBlur = () => {
    onUpdate({ ...config, output_file_path: filePath });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="outputPath">Output File Name</Label>
        <Input
          id="outputPath"
          value={filePath}
          onChange={(e) => setFilePath(e.target.value)}
          onBlur={handleBlur}
          placeholder="output.csv"
        />
        <p className="text-xs text-gray-400 mt-1">CSV file will be generated after execution</p>
      </div>
    </div>
  );
}
