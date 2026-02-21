package com.workflow.segment.config;

import com.workflow.segment.model.DataMart;
import com.workflow.segment.repository.DataMartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataMartSeederTest {
    @Autowired
    private DataMartRepository dataMartRepository;

    @Test
    void shouldLoadSeedDataOnStartup() {
        List<DataMart> dataMarts = dataMartRepository.findAll();
        assertThat(dataMarts).isNotEmpty();
        assertThat(dataMarts).hasSizeGreaterThanOrEqualTo(3);

        DataMart customers = dataMartRepository.findByTableName("customers").orElseThrow();
        assertThat(customers.getDescription()).isNotBlank();
        assertThat(customers.getColumns()).isNotEmpty();
        assertThat(customers.getColumns().get(0).getColumnName()).isNotBlank();
    }
}
