'use client';

import { useCallback, useMemo, useEffect } from 'react';
import {
  ReactFlow,
  useNodesState,
  useEdgesState,
  Background,
  Controls,
  type Node,
  type Edge,
  type NodeTypes,
  type Connection,
  type OnConnect,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import Dagre from 'dagre';
import type { NodeResponse } from '@/lib/types';
import { StartFileUploadNode } from './nodes/start-file-upload-node';
import { StartQueryNode } from './nodes/start-query-node';
import { FilterNode } from './nodes/filter-node';
import { EnrichNode } from './nodes/enrich-node';
import { SplitNode } from './nodes/split-node';
import { JoinNode } from './nodes/join-node';
import { StopNode } from './nodes/stop-node';

const nodeTypes: NodeTypes = {
  START_FILE_UPLOAD: StartFileUploadNode,
  START_QUERY: StartQueryNode,
  FILTER: FilterNode,
  ENRICH: EnrichNode,
  SPLIT: SplitNode,
  JOIN: JoinNode,
  STOP: StopNode,
};

interface WorkflowCanvasProps {
  nodes: NodeResponse[];
  onNodeSelect: (nodeId: string | null) => void;
  onConnect: (sourceId: string, targetId: string) => void;
  onDeleteNode: (nodeId: string) => void;
  workflowId: string;
  onReload: () => void;
}

function getLayoutedElements(nodes: Node[], edges: Edge[]) {
  const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'TB', ranksep: 80, nodesep: 60 });

  nodes.forEach((node) => g.setNode(node.id, { width: 200, height: 80 }));
  edges.forEach((edge) => g.setEdge(edge.source, edge.target));

  Dagre.layout(g);

  const layoutedNodes = nodes.map((node) => {
    const pos = g.node(node.id);
    return { ...node, position: { x: pos.x - 100, y: pos.y - 40 } };
  });

  return { nodes: layoutedNodes, edges };
}

export function WorkflowCanvas({ nodes: apiNodes, onNodeSelect, onConnect, onDeleteNode: _onDeleteNode, workflowId, onReload: _onReload }: WorkflowCanvasProps) {
  const { initialNodes, initialEdges } = useMemo(() => {
    const rfNodes: Node[] = apiNodes.map((n) => ({
      id: n.id,
      type: n.type,
      position: { x: 0, y: 0 },
      data: { ...n.config, nodeType: n.type, nodeId: n.id, workflowId },
    }));

    const rfEdges: Edge[] = apiNodes.flatMap((n) =>
      n.parentNodeIds.map((parentId) => ({
        id: `${parentId}-${n.id}`,
        source: parentId,
        target: n.id,
        animated: false,
        style: { stroke: '#64748b', strokeWidth: 2 },
      }))
    );

    const { nodes: layouted, edges } = getLayoutedElements(rfNodes, rfEdges);
    return { initialNodes: layouted, initialEdges: edges };
  }, [apiNodes, workflowId]);

  const [rfNodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [rfEdges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  useEffect(() => {
    setNodes(initialNodes);
    setEdges(initialEdges);
  }, [initialNodes, initialEdges, setNodes, setEdges]);

  const handleConnect: OnConnect = useCallback(
    (connection: Connection) => {
      if (connection.source && connection.target) {
        onConnect(connection.source, connection.target);
      }
    },
    [onConnect]
  );

  const handleNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      onNodeSelect(node.id);
    },
    [onNodeSelect]
  );

  const handlePaneClick = useCallback(() => {
    onNodeSelect(null);
  }, [onNodeSelect]);

  return (
    <ReactFlow
      nodes={rfNodes}
      edges={rfEdges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={handleConnect}
      onNodeClick={handleNodeClick}
      onPaneClick={handlePaneClick}
      nodeTypes={nodeTypes}
      fitView
      proOptions={{ hideAttribution: true }}
    >
      <Background />
      <Controls />
    </ReactFlow>
  );
}
