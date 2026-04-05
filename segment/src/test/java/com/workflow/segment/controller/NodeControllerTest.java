package com.workflow.segment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.segment.dto.AddNodeRequest;
import com.workflow.segment.dto.CreateWorkflowRequest;
import com.workflow.segment.dto.UpdateNodeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NodeControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    private String workflowId;

    @BeforeEach
    void setUp() throws Exception {
        var result = mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkflowRequest("Node Test WF", "tester")))).andReturn();
        workflowId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void shouldAddStartNode() throws Exception {
        mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddNodeRequest("START_FILE_UPLOAD", Map.of("file_path", "/data/customers.csv")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("START_FILE_UPLOAD"));
    }

    @Test
    void shouldAddFilterNodeWithParent() throws Exception {
        mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddNodeRequest("START_FILE_UPLOAD", Map.of("file_path", "/data/customers.csv"))))).andReturn();

        mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddNodeRequest("FILTER", Map.of("data_mart_table", "purchases", "mode", "JOIN")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FILTER"));
    }

    @Test
    void shouldUpdateNodeConfig() throws Exception {
        var result = mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddNodeRequest("START_QUERY", Map.of("raw_sql", "SELECT * FROM customers"))))).andReturn();
        String nodeId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/workflows/" + workflowId + "/nodes/" + nodeId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNodeRequest(Map.of("raw_sql", "SELECT * FROM customers WHERE active = true")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.raw_sql").value("SELECT * FROM customers WHERE active = true"));
    }

    @Test
    void shouldDeleteNode() throws Exception {
        var result = mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddNodeRequest("START_FILE_UPLOAD", Map.of("file_path", "/data/test.csv"))))).andReturn();
        String nodeId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(delete("/api/workflows/" + workflowId + "/nodes/" + nodeId)).andExpect(status().isNoContent());
    }
}
