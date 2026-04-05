package com.workflow.segment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.segment.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowServiceEdgeValidationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String createWorkflow() throws Exception {
        var result = mockMvc.perform(post("/api/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("Edge Test", "tester"))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void shouldSaveWorkflowWithNodesAndEdges() throws Exception {
        String wfId = createWorkflow();

        var request = new SaveWorkflowRequest(
                List.of(
                        new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1")),
                        new SaveWorkflowRequest.SaveNodeRequest("n2", "STOP", Map.of())
                ),
                List.of(
                        new SaveEdgeRequest(null, "flow", "n1", "n2", null, null, null, false, null, null)
                )
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].name").value("flow"));
    }

    @Test
    void shouldRejectSelfReferencingEdge() throws Exception {
        String wfId = createWorkflow();

        var request = new SaveWorkflowRequest(
                List.of(new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1"))),
                List.of(new SaveEdgeRequest(null, "self", "n1", "n1", null, null, null, false, null, null))
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEdgeReferencingNonExistentNode() throws Exception {
        String wfId = createWorkflow();

        var request = new SaveWorkflowRequest(
                List.of(new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1"))),
                List.of(new SaveEdgeRequest(null, "bad", "n1", "n999", null, null, null, false, null, null))
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEdgesInGetWorkflow() throws Exception {
        String wfId = createWorkflow();

        var saveRequest = new SaveWorkflowRequest(
                List.of(
                        new SaveWorkflowRequest.SaveNodeRequest("n1", "START_QUERY", Map.of("raw_sql", "SELECT 1")),
                        new SaveWorkflowRequest.SaveNodeRequest("n2", "FILTER", Map.of()),
                        new SaveWorkflowRequest.SaveNodeRequest("n3", "STOP", Map.of())
                ),
                List.of(
                        new SaveEdgeRequest(null, "step1", "n1", "n2", null, null, null, false, null, null),
                        new SaveEdgeRequest(null, "step2", "n2", "n3", null, null, null, false, null, null)
                )
        );

        mockMvc.perform(put("/api/workflows/" + wfId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saveRequest)));

        mockMvc.perform(get("/api/workflows/" + wfId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(2))
                .andExpect(jsonPath("$.edges[0].sourceNodeId").exists())
                .andExpect(jsonPath("$.edges[0].targetNodeId").exists());
    }
}
