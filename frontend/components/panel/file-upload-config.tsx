'use client';

import { useState } from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

interface FileUploadConfigProps {
  config: Record<string, unknown>;
  onUpdate: (config: Record<string, unknown>) => void;
}

export function FileUploadConfig({ config, onUpdate }: FileUploadConfigProps) {
  const [filePath, setFilePath] = useState(String(config.file_path || ''));

  const handleBlur = () => {
    onUpdate({ ...config, file_path: filePath });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="filePath">File Path</Label>
        <Input
          id="filePath"
          value={filePath}
          onChange={(e) => setFilePath(e.target.value)}
          onBlur={handleBlur}
          placeholder="/path/to/data.csv"
        />
        <p className="text-xs text-gray-400 mt-1">Path to CSV file on disk</p>
      </div>
    </div>
  );
}
