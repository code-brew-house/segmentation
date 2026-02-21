package com.workflow.segment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataMartControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListAllDataMarts() throws Exception {
        mockMvc.perform(get("/api/data-marts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].tableName").exists())
                .andExpect(jsonPath("$[0].description").exists())
                .andExpect(jsonPath("$[0].columnCount").isNumber());
    }

    @Test
    void shouldReturn404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/data-marts/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
