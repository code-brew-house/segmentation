'use client';

import { useState } from 'react';
import { api } from '@/lib/api';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Loader2 } from 'lucide-react';

interface SqlPreviewDialogProps {
  open: boolean;
  onClose: () => void;
  workflowId: string;
  nodeId: string;
  nodeType: string;
  config: Record<string, unknown>;
}

export function SqlPreviewDialog({ open, onClose, workflowId, nodeId, nodeType, config }: SqlPreviewDialogProps) {
  const [sql, setSql] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchPreview = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.sqlPreview(workflowId, nodeId, { nodeType, config });
      setSql(result.sql);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate SQL preview');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenChange = (isOpen: boolean) => {
    if (isOpen) {
      fetchPreview();
    } else {
      setSql(null);
      setError(null);
      onClose();
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>SQL Preview</DialogTitle>
        </DialogHeader>
        <div className="mt-2">
          {loading && (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            </div>
          )}
          {error && (
            <div className="p-3 bg-red-50 rounded text-red-700 text-sm">{error}</div>
          )}
          {sql && (
            <pre className="bg-gray-900 text-green-400 p-4 rounded-lg text-sm overflow-auto max-h-[400px] font-mono whitespace-pre-wrap">
              {sql}
            </pre>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
