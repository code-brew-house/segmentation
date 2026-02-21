'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { Workflow } from '@/lib/types';
import { WorkflowCard } from '@/components/dashboard/workflow-card';
import { CreateWorkflowDialog } from '@/components/dashboard/create-workflow-dialog';
import { ExecutionHistoryList } from '@/components/dashboard/execution-history-list';
import { useRouter } from 'next/navigation';

type DashboardTab = 'workflows' | 'history';

export default function DashboardPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<DashboardTab>('workflows');
  const router = useRouter();

  const loadWorkflows = async () => {
    try {
      const data = await api.listWorkflows();
      setWorkflows(data);
    } catch (err) {
      console.error('Failed to load workflows:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadWorkflows(); }, []);

  const handleCreate = async (name: string, createdBy: string) => {
    try {
      const wf = await api.createWorkflow({ name, createdBy: createdBy || undefined });
      router.push(`/workflow/${wf.id}`);
    } catch (err) {
      console.error('Failed to create workflow:', err);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await api.deleteWorkflow(id);
      setWorkflows((prev) => prev.filter((w) => w.id !== id));
    } catch (err) {
      console.error('Failed to delete workflow:', err);
    }
  };

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-6">
          <button
            onClick={() => setActiveTab('workflows')}
            className={`text-2xl font-bold pb-1 border-b-2 transition-colors ${
              activeTab === 'workflows' ? 'border-black text-black' : 'border-transparent text-gray-400 hover:text-gray-600'
            }`}
          >
            Workflows
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`text-2xl font-bold pb-1 border-b-2 transition-colors ${
              activeTab === 'history' ? 'border-black text-black' : 'border-transparent text-gray-400 hover:text-gray-600'
            }`}
          >
            History
          </button>
        </div>
        {activeTab === 'workflows' && <CreateWorkflowDialog onCreate={handleCreate} />}
      </div>

      {activeTab === 'workflows' && (
        <>
          {loading ? (
            <p className="text-gray-500">Loading workflows...</p>
          ) : workflows.length === 0 ? (
            <div className="text-center py-16">
              <p className="text-gray-500 text-lg">No workflows yet</p>
              <p className="text-gray-400 mt-1">Create your first workflow to get started</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {workflows.map((wf) => (
                <WorkflowCard key={wf.id} workflow={wf} onDelete={handleDelete} />
              ))}
            </div>
          )}
        </>
      )}

      {activeTab === 'history' && <ExecutionHistoryList />}
    </div>
  );
}
