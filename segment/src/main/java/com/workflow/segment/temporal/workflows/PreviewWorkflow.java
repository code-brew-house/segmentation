package com.workflow.segment.temporal.workflows;

import com.workflow.segment.temporal.model.PreviewInput;
import com.workflow.segment.temporal.model.PreviewResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PreviewWorkflow {
    @WorkflowMethod
    PreviewResult execute(PreviewInput input);
}
