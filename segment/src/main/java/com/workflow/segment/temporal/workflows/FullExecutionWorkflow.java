package com.workflow.segment.temporal.workflows;

import com.workflow.segment.temporal.model.FullExecutionInput;
import com.workflow.segment.temporal.model.FullExecutionResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface FullExecutionWorkflow {
    @WorkflowMethod
    FullExecutionResult execute(FullExecutionInput input);
}
