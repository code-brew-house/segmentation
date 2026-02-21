package com.workflow.segment.config;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
@Profile("!test")
public class SampleDataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        createAndSeedCustomers();
        createAndSeedPurchases();
        createAndSeedDemographics();
        log.info("Sample data seeding complete");
    }

    private void createAndSeedCustomers() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS customers (
                customer_id INTEGER PRIMARY KEY,
                name VARCHAR(255),
                email VARCHAR(255),
                city VARCHAR(100),
                age INTEGER,
                created_at TIMESTAMP
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Integer.class);
        if (count != null && count > 0) {
            log.info("Customers table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/customers.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext(); // skip header
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO customers (customer_id, name, email, city, age, created_at) VALUES (?, ?, ?, ?, ?, ?::timestamp)",
                    Integer.parseInt(line[0]), line[1], line[2], line[3],
                    Integer.parseInt(line[4]), line[5]
                );
            }
        }
        log.info("Seeded customers table with sample data");
    }

    private void createAndSeedPurchases() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS purchases (
                purchase_id INTEGER PRIMARY KEY,
                customer_id INTEGER,
                product_name VARCHAR(255),
                amount DECIMAL(10,2),
                purchase_date DATE,
                category VARCHAR(100)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class);
        if (count != null && count > 0) {
            log.info("Purchases table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/purchases.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext(); // skip header
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO purchases (purchase_id, customer_id, product_name, amount, purchase_date, category) VALUES (?, ?, ?, ?, ?::date, ?)",
                    Integer.parseInt(line[0]), Integer.parseInt(line[1]), line[2],
                    new java.math.BigDecimal(line[3]), line[4], line[5]
                );
            }
        }
        log.info("Seeded purchases table with sample data");
    }

    private void createAndSeedDemographics() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS demographics (
                customer_id INTEGER PRIMARY KEY,
                income_bracket VARCHAR(50),
                education_level VARCHAR(50),
                occupation VARCHAR(100),
                marital_status VARCHAR(20)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM demographics", Integer.class);
        if (count != null && count > 0) {
            log.info("Demographics table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/demographics.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext(); // skip header
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO demographics (customer_id, income_bracket, education_level, occupation, marital_status) VALUES (?, ?, ?, ?, ?)",
                    Integer.parseInt(line[0]), line[1], line[2], line[3], line[4]
                );
            }
        }
        log.info("Seeded demographics table with sample data");
    }
}
