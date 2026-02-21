package com.workflow.segment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.segment.dto.CreateWorkflowRequest;
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
class WorkflowControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldCreateWorkflow() throws Exception {
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("Test WF", "tester"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test WF"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldListWorkflows() throws Exception {
        mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("List Test", "tester"))));
        mockMvc.perform(get("/api/workflows")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetWorkflowById() throws Exception {
        var result = mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("Get Test", "tester")))).andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/workflows/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Get Test")).andExpect(jsonPath("$.nodes").isArray());
    }

    @Test
    void shouldDeleteWorkflow() throws Exception {
        var result = mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("Delete Test", "tester")))).andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(delete("/api/workflows/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/workflows/" + id)).andExpect(status().isNotFound());
    }
}
