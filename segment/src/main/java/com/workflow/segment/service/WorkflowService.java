package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.SegmentWorkflow;
import com.workflow.segment.model.WorkflowStatus;
import com.workflow.segment.repository.SegmentWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final SegmentWorkflowRepository workflowRepository;

    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        SegmentWorkflow wf = new SegmentWorkflow();
        wf.setName(request.name());
        wf.setCreatedBy(request.createdBy());
        wf.setStatus(WorkflowStatus.DRAFT);
        wf = workflowRepository.save(wf);
        return toResponse(wf);
    }

    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDetailResponse getWorkflow(UUID id) {
        SegmentWorkflow wf = workflowRepository.findById(id).orElseThrow(() -> new WorkflowNotFoundException(id));
        return new WorkflowDetailResponse(wf.getId(), wf.getName(), wf.getCreatedBy(), wf.getCreatedAt(), wf.getStatus(),
                wf.getNodes().stream().map(n -> new NodeResponse(n.getId(), n.getType(), n.getParentNodeIds(), n.getConfig(), n.getPosition())).toList());
    }

    @Transactional
    public void deleteWorkflow(UUID id) {
        if (!workflowRepository.existsById(id)) throw new WorkflowNotFoundException(id);
        workflowRepository.deleteById(id);
    }

    private WorkflowResponse toResponse(SegmentWorkflow wf) {
        return new WorkflowResponse(wf.getId(), wf.getName(), wf.getCreatedBy(), wf.getCreatedAt(), wf.getStatus(), wf.getNodes().size());
    }
}
