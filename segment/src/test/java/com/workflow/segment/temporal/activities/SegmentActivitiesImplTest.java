package com.workflow.segment.temporal.activities;

import com.workflow.segment.temporal.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SegmentActivitiesImplTest {

    @Autowired
    private SegmentActivitiesImpl activities;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Create a test source table
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_source");
        jdbcTemplate.execute("CREATE TABLE test_source (customer_id INT, name VARCHAR(100), city VARCHAR(100), age INT)");
        jdbcTemplate.update("INSERT INTO test_source VALUES (1, 'Alice', 'Mumbai', 30)");
        jdbcTemplate.update("INSERT INTO test_source VALUES (2, 'Bob', 'Delhi', 25)");
        jdbcTemplate.update("INSERT INTO test_source VALUES (3, 'Charlie', 'Mumbai', 35)");
        jdbcTemplate.update("INSERT INTO test_source VALUES (4, 'Diana', 'Pune', 28)");

        // Create a data mart table
        jdbcTemplate.execute("DROP TABLE IF EXISTS dm_purchases");
        jdbcTemplate.execute("CREATE TABLE dm_purchases (customer_id INT, amount DECIMAL(10,2), category VARCHAR(50))");
        jdbcTemplate.update("INSERT INTO dm_purchases VALUES (1, 500.00, 'Electronics')");
        jdbcTemplate.update("INSERT INTO dm_purchases VALUES (2, 200.00, 'Books')");
        jdbcTemplate.update("INSERT INTO dm_purchases VALUES (3, 800.00, 'Electronics')");

        // Create enrichment table
        jdbcTemplate.execute("DROP TABLE IF EXISTS dm_demographics");
        jdbcTemplate.execute("CREATE TABLE dm_demographics (customer_id INT, income_bracket VARCHAR(50))");
        jdbcTemplate.update("INSERT INTO dm_demographics VALUES (1, 'High')");
        jdbcTemplate.update("INSERT INTO dm_demographics VALUES (2, 'Medium')");
        jdbcTemplate.update("INSERT INTO dm_demographics VALUES (3, 'High')");
        jdbcTemplate.update("INSERT INTO dm_demographics VALUES (4, 'Low')");
    }

    @Test
    void fileUpload_shouldIngestCsv() throws Exception {
        Path csvFile = tempDir.resolve("test.csv");
        try (FileWriter fw = new FileWriter(csvFile.toFile())) {
            fw.write("id,name,city\n");
            fw.write("1,Alice,Mumbai\n");
            fw.write("2,Bob,Delhi\n");
        }
        var result = activities.fileUploadActivity(new FileUploadInput(csvFile.toString(), "test_upload", null));
        assertThat(result.getResultTable()).isEqualTo("test_upload");
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.getColumns()).containsExactly("id", "name", "city");
    }

    @Test
    void startQuery_shouldCreateTableFromSql() {
        var result = activities.startQueryActivity(new StartQueryInput("SELECT * FROM test_source WHERE age >= 30", "test_query_result"));
        assertThat(result.getResultTable()).isEqualTo("test_query_result");
        assertThat(result.getRowCount()).isEqualTo(2); // Alice(30) and Charlie(35)
    }

    @Test
    void filter_joinMode_shouldFilterWithJoin() {
        var conditions = Map.<String, Object>of(
                "operation", "AND",
                "conditions", List.of(Map.of("field", "dm.amount", "operator", ">", "value", "300")));
        var result = activities.filterActivity(new FilterInput(
                "test_source", "test_filter_join", "dm_purchases", "customer_id", "JOIN", conditions, false));
        assertThat(result.getResultTable()).isEqualTo("test_filter_join");
        assertThat(result.getOutputCount()).isEqualTo(2); // Alice(500) and Charlie(800)
        assertThat(result.getInputCount()).isEqualTo(4);
        assertThat(result.getFilteredCount()).isEqualTo(2);
    }

    @Test
    void filter_subqueryMode_shouldFilterWithSubquery() {
        var conditions = Map.<String, Object>of(
                "operation", "AND",
                "conditions", List.of(Map.of("field", "amount", "operator", ">", "value", "300")));
        var result = activities.filterActivity(new FilterInput(
                "test_source", "test_filter_sub", "dm_purchases", "customer_id", "SUBQUERY", conditions, false));
        assertThat(result.getOutputCount()).isEqualTo(2); // customers 1 and 3
    }

    @Test
    void enrich_addColumns_shouldJoinColumns() {
        var result = activities.enrichActivity(new EnrichInput(
                "test_source", "test_enrich_cols", "dm_demographics", "ADD_COLUMNS", "customer_id", List.of("income_bracket")));
        assertThat(result.getResultTable()).isEqualTo("test_enrich_cols");
        assertThat(result.getOutputCount()).isEqualTo(4); // same row count, extra column
    }

    @Test
    void enrich_addRecords_shouldUnion() {
        // Create a compatible table for union
        jdbcTemplate.execute("DROP TABLE IF EXISTS dm_extra_customers");
        jdbcTemplate.execute("CREATE TABLE dm_extra_customers (customer_id INT, name VARCHAR(100), city VARCHAR(100), age INT)");
        jdbcTemplate.update("INSERT INTO dm_extra_customers VALUES (5, 'Eve', 'Chennai', 22)");

        var result = activities.enrichActivity(new EnrichInput(
                "test_source", "test_enrich_rows", "dm_extra_customers", "ADD_RECORDS", null, null));
        assertThat(result.getOutputCount()).isEqualTo(5); // 4 + 1
        assertThat(result.getAddedCount()).isEqualTo(1);
    }

    @Test
    void stopNode_shouldExportCsv() {
        String outputPath = tempDir.resolve("output.csv").toString();
        var result = activities.stopNodeActivity(new StopNodeInput("test_source", outputPath));
        assertThat(result.getRowCount()).isEqualTo(4);
        assertThat(new java.io.File(outputPath)).exists();
    }
}
