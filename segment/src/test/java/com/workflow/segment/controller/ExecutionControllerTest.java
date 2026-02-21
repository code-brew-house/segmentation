package com.workflow.segment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.segment.dto.CreateWorkflowRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    private String workflowId;

    @BeforeEach
    void setUp() throws Exception {
        var result = mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("Exec Test WF", "tester")))).andReturn();
        workflowId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void shouldListExecutions() throws Exception {
        mockMvc.perform(get("/api/workflows/" + workflowId + "/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
