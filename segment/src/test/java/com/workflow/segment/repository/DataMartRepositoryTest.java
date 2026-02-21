package com.workflow.segment.repository;

import com.workflow.segment.model.DataMart;
import com.workflow.segment.model.DataMartColumn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class DataMartRepositoryTest {

    @Autowired
    private DataMartRepository dataMartRepository;

    @Test
    void shouldSaveAndFindDataMart() {
        DataMart dm = new DataMart();
        dm.setTableName("customers");
        dm.setSchemaName("public");
        dm.setDescription("Customer master data");

        DataMartColumn col = new DataMartColumn();
        col.setColumnName("customer_id");
        col.setDataType("INTEGER");
        col.setDescription("Unique ID");
        col.setOrdinalPosition(1);
        col.setDataMart(dm);
        dm.setColumns(List.of(col));

        DataMart saved = dataMartRepository.save(dm);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTableName()).isEqualTo("customers");
        assertThat(saved.getColumns()).hasSize(1);
        assertThat(saved.getColumns().get(0).getColumnName()).isEqualTo("customer_id");
    }

    @Test
    void shouldFindByTableName() {
        DataMart dm = new DataMart();
        dm.setTableName("purchases");
        dm.setSchemaName("public");
        dm.setDescription("Purchase history");
        dataMartRepository.save(dm);

        var found = dataMartRepository.findByTableName("purchases");
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isEqualTo("Purchase history");
    }
}
