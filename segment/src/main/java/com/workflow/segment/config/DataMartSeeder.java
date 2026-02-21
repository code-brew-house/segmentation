package com.workflow.segment.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.segment.model.DataMart;
import com.workflow.segment.model.DataMartColumn;
import com.workflow.segment.repository.DataMartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class DataMartSeeder implements CommandLineRunner {
    private final DataMartRepository dataMartRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.data-mart.seed-file}")
    private Resource seedFile;

    @Override
    public void run(String... args) throws Exception {
        if (dataMartRepository.count() > 0) {
            log.info("Data marts already seeded, skipping");
            return;
        }
        JsonNode root = objectMapper.readTree(seedFile.getInputStream());
        JsonNode dataMarts = root.get("data_marts");
        for (JsonNode dmNode : dataMarts) {
            DataMart dm = new DataMart();
            dm.setTableName(dmNode.get("table_name").asText());
            dm.setSchemaName(dmNode.get("schema_name").asText());
            dm.setDescription(dmNode.get("description").asText());
            var columns = new ArrayList<DataMartColumn>();
            JsonNode colNodes = dmNode.get("columns");
            for (int i = 0; i < colNodes.size(); i++) {
                JsonNode colNode = colNodes.get(i);
                DataMartColumn col = new DataMartColumn();
                col.setColumnName(colNode.get("column_name").asText());
                col.setDataType(colNode.get("data_type").asText());
                col.setDescription(colNode.get("description").asText());
                col.setOrdinalPosition(i + 1);
                col.setDataMart(dm);
                columns.add(col);
            }
            dm.setColumns(columns);
            dataMartRepository.save(dm);
            log.info("Seeded data mart: {}", dm.getTableName());
        }
    }
}
