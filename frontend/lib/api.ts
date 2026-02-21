import type {
  DataMart,
  DataMartDetail,
  Workflow,
  WorkflowDetail,
  NodeResponse,
  AddNodeRequest,
  UpdateNodeRequest,
  ExecutionResponse,
  ExecutionDetail,
  PreviewResponse,
} from './types';

export type {
  DataMart,
  DataMartDetail,
  Workflow,
  WorkflowDetail,
  NodeResponse,
  AddNodeRequest,
  UpdateNodeRequest,
  ExecutionResponse,
  ExecutionDetail,
  PreviewResponse,
};

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

async function fetchApi<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`API error ${res.status}: ${text}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export const api = {
  // Data Marts
  listDataMarts: () => fetchApi<DataMart[]>('/data-marts'),
  getDataMart: (id: string) => fetchApi<DataMartDetail>(`/data-marts/${id}`),

  // Workflows
  listWorkflows: () => fetchApi<Workflow[]>('/workflows'),
  createWorkflow: (data: { name: string; createdBy?: string }) =>
    fetchApi<Workflow>('/workflows', { method: 'POST', body: JSON.stringify(data) }),
  getWorkflow: (id: string) => fetchApi<WorkflowDetail>(`/workflows/${id}`),
  deleteWorkflow: (id: string) => fetchApi<void>(`/workflows/${id}`, { method: 'DELETE' }),

  // Nodes
  addNode: (workflowId: string, data: AddNodeRequest) =>
    fetchApi<NodeResponse>(`/workflows/${workflowId}/nodes`, { method: 'POST', body: JSON.stringify(data) }),
  updateNode: (workflowId: string, nodeId: string, data: UpdateNodeRequest) =>
    fetchApi<NodeResponse>(`/workflows/${workflowId}/nodes/${nodeId}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteNode: (workflowId: string, nodeId: string) =>
    fetchApi<void>(`/workflows/${workflowId}/nodes/${nodeId}`, { method: 'DELETE' }),

  // Execution
  executeWorkflow: (workflowId: string) =>
    fetchApi<ExecutionResponse>(`/workflows/${workflowId}/execute`, { method: 'POST' }),
  listExecutions: (workflowId: string) =>
    fetchApi<ExecutionResponse[]>(`/workflows/${workflowId}/executions`),
  getExecution: (workflowId: string, execId: string) =>
    fetchApi<ExecutionDetail>(`/workflows/${workflowId}/executions/${execId}`),
  getNodeResults: (workflowId: string, execId: string, nodeId: string) =>
    fetchApi<Record<string, unknown>[]>(`/workflows/${workflowId}/executions/${execId}/nodes/${nodeId}/results`),
  downloadCsvUrl: (workflowId: string, execId: string, nodeId: string) =>
    `${API_BASE}/workflows/${workflowId}/executions/${execId}/nodes/${nodeId}/download`,

  // Preview
  previewNode: (workflowId: string, nodeId: string) =>
    fetchApi<PreviewResponse>(`/workflows/${workflowId}/nodes/${nodeId}/preview`, { method: 'POST' }),
};
