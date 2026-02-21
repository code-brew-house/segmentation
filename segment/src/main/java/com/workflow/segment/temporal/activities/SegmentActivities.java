package com.workflow.segment.temporal.activities;

import com.workflow.segment.temporal.model.*;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface SegmentActivities {
    @ActivityMethod
    FileUploadResult fileUploadActivity(FileUploadInput input);

    @ActivityMethod
    StartQueryResult startQueryActivity(StartQueryInput input);

    @ActivityMethod
    FilterResult filterActivity(FilterInput input);

    @ActivityMethod
    EnrichResult enrichActivity(EnrichInput input);

    @ActivityMethod
    StopNodeResult stopNodeActivity(StopNodeInput input);

    /** Persist all node results and mark the execution as SUCCESS or FAILED in the DB. */
    @ActivityMethod
    void completeExecution(String executionId, List<NodeResult> nodeResults, String finalStatus);
}
