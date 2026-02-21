export interface DataMart {
  id: string;
  tableName: string;
  schemaName: string;
  description: string;
  columnCount: number;
}

export interface DataMartColumn {
  id: string;
  columnName: string;
  dataType: string;
  description: string;
  ordinalPosition: number;
}

export interface DataMartDetail extends Omit<DataMart, 'columnCount'> {
  columns: DataMartColumn[];
}

export interface Workflow {
  id: string;
  name: string;
  createdBy: string;
  createdAt: string;
  status: 'DRAFT' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  nodeCount: number;
}

export interface WorkflowDetail extends Omit<Workflow, 'nodeCount'> {
  nodes: NodeResponse[];
}

export type NodeType = 'START_FILE_UPLOAD' | 'START_QUERY' | 'FILTER' | 'ENRICH' | 'SPLIT' | 'JOIN' | 'STOP';

export interface NodeResponse {
  id: string;
  type: NodeType;
  parentNodeIds: string[];
  config: Record<string, unknown>;
  position: number | null;
}

export interface AddNodeRequest {
  parentNodeIds: string[];
  type: string;
  config: Record<string, unknown>;
  position?: number;
}

export interface UpdateNodeRequest {
  parentNodeIds?: string[];
  config?: Record<string, unknown>;
}

export interface ExecutionResponse {
  id: string;
  workflowId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  startedAt: string;
  completedAt: string | null;
}

export interface NodeExecutionResult {
  nodeId: string;
  nodeType: NodeType;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  inputRecordCount: number | null;
  filteredRecordCount: number | null;
  outputRecordCount: number | null;
  resultTableName: string | null;
  outputFilePath: string | null;
  errorMessage: string | null;
}

export interface ExecutionDetail extends ExecutionResponse {
  nodeResults: NodeExecutionResult[];
}

export interface PreviewResponse {
  executionId: string;
  workflowId: string;
  nodeId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
}

export interface ExecutionHistoryItem {
  executionId: string;
  workflowId: string;
  workflowName: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  startedAt: string;
  completedAt: string | null;
  totalNodes: number;
  passedNodes: number;
  failedNodes: number;
}

export interface SaveWorkflowRequest {
  nodes: SaveNodeRequest[];
}

export interface SaveNodeRequest {
  id: string | null;
  type: string;
  parentNodeIds: string[];
  config: Record<string, unknown>;
  position: number | null;
}

export interface SqlPreviewResponse {
  sql: string;
}
