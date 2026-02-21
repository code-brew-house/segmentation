'use client';

import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus } from 'lucide-react';

interface CreateWorkflowDialogProps {
  onCreate: (name: string, createdBy: string) => void;
}

export function CreateWorkflowDialog({ onCreate }: CreateWorkflowDialogProps) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [createdBy, setCreatedBy] = useState('');

  const handleCreate = () => {
    if (!name.trim()) return;
    onCreate(name.trim(), createdBy.trim());
    setName('');
    setCreatedBy('');
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4 mr-2" />
          New Workflow
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create New Workflow</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 pt-4">
          <div>
            <Label htmlFor="name">Workflow Name</Label>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g., High Value Customers" />
          </div>
          <div>
            <Label htmlFor="createdBy">Created By (optional)</Label>
            <Input id="createdBy" value={createdBy} onChange={(e) => setCreatedBy(e.target.value)} placeholder="e.g., Campaign Manager" />
          </div>
          <Button onClick={handleCreate} disabled={!name.trim()} className="w-full">
            Create Workflow
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
