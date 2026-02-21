package com.workflow.segment.temporal.config;

import com.workflow.segment.temporal.activities.SegmentActivities;
import com.workflow.segment.temporal.workflows.FullExecutionWorkflowImpl;
import com.workflow.segment.temporal.workflows.PreviewWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "segment-workflow-queue";

    @Bean
    @Profile("!test")
    public WorkflowServiceStubs workflowServiceStubs(
            @Value("${spring.temporal.connection.target:localhost:7233}") String target) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    @Profile("!test")
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder().setNamespace("default").build());
    }

    @Bean
    @Profile("!test")
    public WorkerFactory workerFactory(WorkflowClient client, SegmentActivities activities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(PreviewWorkflowImpl.class, FullExecutionWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
