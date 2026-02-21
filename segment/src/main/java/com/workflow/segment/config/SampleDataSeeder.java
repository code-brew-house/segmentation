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
import java.math.BigDecimal;
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
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS banking");

        createAndSeedCustomers();
        createAndSeedPurchases();
        createAndSeedDemographics();
        createAndSeedBranchOffices();
        createAndSeedAccounts();
        createAndSeedTransactions();
        createAndSeedLoans();
        createAndSeedCreditCards();
        createAndSeedMortgages();
        createAndSeedFraudAlerts();
        createAndSeedRiskScores();
        createAndSeedInvestments();
        createAndSeedKycDocuments();
        createAndSeedInterestRates();
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
            reader.readNext(); // skip header
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
            reader.readNext(); // skip header
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO purchases (purchase_id, customer_id, product_name, amount, purchase_date, category) VALUES (?, ?, ?, ?, ?::date, ?)",
                    Integer.parseInt(line[0]), Integer.parseInt(line[1]), line[2],
                    new BigDecimal(line[3]), line[4], line[5]
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
            reader.readNext(); // skip header
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

    private void createAndSeedBranchOffices() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.branch_offices (
                branch_id INTEGER PRIMARY KEY,
                branch_name VARCHAR(255),
                branch_type VARCHAR(50),
                address VARCHAR(500),
                city VARCHAR(100),
                state CHAR(2),
                zip_code VARCHAR(10),
                phone VARCHAR(20),
                manager_id INTEGER,
                atm_count INTEGER,
                opened_date DATE,
                status VARCHAR(20)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.branch_offices", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.branch_offices table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/branch_offices.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.branch_offices (branch_id, branch_name, branch_type, address, city, state, zip_code, phone, manager_id, atm_count, opened_date, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?)",
                    Integer.parseInt(line[0]), line[1], line[2], line[3], line[4], line[5],
                    line[6], line[7], Integer.parseInt(line[8]), Integer.parseInt(line[9]), line[10], line[11]
                );
            }
        }
        log.info("Seeded banking.branch_offices table with sample data");
    }

    private void createAndSeedAccounts() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.accounts (
                account_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                account_number VARCHAR(20),
                account_type VARCHAR(50),
                balance DECIMAL(15,2),
                currency CHAR(3),
                status VARCHAR(20),
                opened_date DATE,
                branch_id INTEGER
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.accounts", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.accounts table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/accounts.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.accounts (account_id, customer_id, account_number, account_type, balance, currency, status, opened_date, branch_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?::date, ?)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), line[2], line[3],
                    new BigDecimal(line[4]), line[5], line[6], line[7], Integer.parseInt(line[8])
                );
            }
        }
        log.info("Seeded banking.accounts table with sample data");
    }

    private void createAndSeedTransactions() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.transactions (
                transaction_id BIGINT PRIMARY KEY,
                account_id BIGINT,
                transaction_type VARCHAR(50),
                amount DECIMAL(15,2),
                currency CHAR(3),
                transaction_date TIMESTAMP,
                description VARCHAR(500),
                merchant_name VARCHAR(255),
                channel VARCHAR(50),
                status VARCHAR(20),
                reference_number VARCHAR(100)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.transactions", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.transactions table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/transactions.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.transactions (transaction_id, account_id, transaction_type, amount, currency, transaction_date, description, merchant_name, channel, status, reference_number) VALUES (?, ?, ?, ?, ?, ?::timestamp, ?, ?, ?, ?, ?)",
                    Long.parseLong(line[0]), Long.parseLong(line[1]), line[2],
                    new BigDecimal(line[3]), line[4], line[5], line[6],
                    line[7].isEmpty() ? null : line[7], line[8], line[9], line[10]
                );
            }
        }
        log.info("Seeded banking.transactions table with sample data");
    }

    private void createAndSeedLoans() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.loans (
                loan_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                loan_type VARCHAR(50),
                principal_amount DECIMAL(15,2),
                outstanding_balance DECIMAL(15,2),
                interest_rate DECIMAL(5,4),
                term_months INTEGER,
                monthly_payment DECIMAL(10,2),
                origination_date DATE,
                maturity_date DATE,
                status VARCHAR(20)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.loans", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.loans table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/loans.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.loans (loan_id, customer_id, loan_type, principal_amount, outstanding_balance, interest_rate, term_months, monthly_payment, origination_date, maturity_date, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?::date, ?)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), line[2],
                    new BigDecimal(line[3]), new BigDecimal(line[4]), new BigDecimal(line[5]),
                    Integer.parseInt(line[6]), new BigDecimal(line[7]), line[8], line[9], line[10]
                );
            }
        }
        log.info("Seeded banking.loans table with sample data");
    }

    private void createAndSeedCreditCards() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.credit_cards (
                card_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                card_number_masked VARCHAR(20),
                card_type VARCHAR(50),
                credit_limit DECIMAL(10,2),
                current_balance DECIMAL(10,2),
                available_credit DECIMAL(10,2),
                apr DECIMAL(5,4),
                payment_due_date DATE,
                minimum_payment DECIMAL(10,2),
                rewards_points INTEGER,
                status VARCHAR(20)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.credit_cards", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.credit_cards table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/credit_cards.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.credit_cards (card_id, customer_id, card_number_masked, card_type, credit_limit, current_balance, available_credit, apr, payment_due_date, minimum_payment, rewards_points, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?, ?, ?)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), line[2], line[3],
                    new BigDecimal(line[4]), new BigDecimal(line[5]), new BigDecimal(line[6]),
                    new BigDecimal(line[7]), line[8], new BigDecimal(line[9]),
                    Integer.parseInt(line[10]), line[11]
                );
            }
        }
        log.info("Seeded banking.credit_cards table with sample data");
    }

    private void createAndSeedMortgages() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.mortgages (
                mortgage_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                property_address VARCHAR(500),
                property_type VARCHAR(50),
                loan_amount DECIMAL(15,2),
                outstanding_balance DECIMAL(15,2),
                interest_rate DECIMAL(5,4),
                rate_type VARCHAR(10),
                term_years INTEGER,
                ltv_ratio DECIMAL(5,4),
                origination_date DATE,
                escrow_balance DECIMAL(10,2),
                status VARCHAR(20)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.mortgages", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.mortgages table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/mortgages.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.mortgages (mortgage_id, customer_id, property_address, property_type, loan_amount, outstanding_balance, interest_rate, rate_type, term_years, ltv_ratio, origination_date, escrow_balance, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?, ?)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), line[2], line[3],
                    new BigDecimal(line[4]), new BigDecimal(line[5]), new BigDecimal(line[6]),
                    line[7], Integer.parseInt(line[8]), new BigDecimal(line[9]),
                    line[10], new BigDecimal(line[11]), line[12]
                );
            }
        }
        log.info("Seeded banking.mortgages table with sample data");
    }

    private void createAndSeedFraudAlerts() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.fraud_alerts (
                alert_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                transaction_id BIGINT,
                alert_type VARCHAR(100),
                severity VARCHAR(20),
                alert_timestamp TIMESTAMP,
                fraud_score DECIMAL(5,4),
                status VARCHAR(30),
                resolved_at TIMESTAMP,
                notes TEXT
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.fraud_alerts", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.fraud_alerts table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/fraud_alerts.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                Long transactionId = line[2].isEmpty() ? null : Long.parseLong(line[2]);
                String resolvedAt = line[8].isEmpty() ? null : line[8];
                jdbcTemplate.update(
                    "INSERT INTO banking.fraud_alerts (alert_id, customer_id, transaction_id, alert_type, severity, alert_timestamp, fraud_score, status, resolved_at, notes) VALUES (?, ?, ?, ?, ?, ?::timestamp, ?, ?, ?::timestamp, ?)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), transactionId,
                    line[3], line[4], line[5], new BigDecimal(line[6]), line[7], resolvedAt, line[9]
                );
            }
        }
        log.info("Seeded banking.fraud_alerts table with sample data");
    }

    private void createAndSeedRiskScores() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.risk_scores (
                score_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                credit_score INTEGER,
                internal_risk_rating VARCHAR(10),
                probability_of_default DECIMAL(5,4),
                debt_to_income_ratio DECIMAL(5,4),
                kyc_status VARCHAR(30),
                aml_risk_level VARCHAR(20),
                score_date DATE,
                next_review_date DATE
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.risk_scores", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.risk_scores table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/risk_scores.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.risk_scores (score_id, customer_id, credit_score, internal_risk_rating, probability_of_default, debt_to_income_ratio, kyc_status, aml_risk_level, score_date, next_review_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?::date)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), Integer.parseInt(line[2]),
                    line[3], new BigDecimal(line[4]), new BigDecimal(line[5]),
                    line[6], line[7], line[8], line[9]
                );
            }
        }
        log.info("Seeded banking.risk_scores table with sample data");
    }

    private void createAndSeedInvestments() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.investments (
                investment_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                account_type VARCHAR(50),
                ticker_symbol VARCHAR(10),
                asset_class VARCHAR(50),
                quantity DECIMAL(15,6),
                cost_basis DECIMAL(15,2),
                market_value DECIMAL(15,2),
                unrealized_gain_loss DECIMAL(15,2),
                risk_profile VARCHAR(30),
                as_of_date DATE
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.investments", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.investments table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/investments.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                jdbcTemplate.update(
                    "INSERT INTO banking.investments (investment_id, customer_id, account_type, ticker_symbol, asset_class, quantity, cost_basis, market_value, unrealized_gain_loss, risk_profile, as_of_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::date)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), line[2], line[3], line[4],
                    new BigDecimal(line[5]), new BigDecimal(line[6]), new BigDecimal(line[7]),
                    new BigDecimal(line[8]), line[9], line[10]
                );
            }
        }
        log.info("Seeded banking.investments table with sample data");
    }

    private void createAndSeedKycDocuments() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.kyc_documents (
                document_id BIGINT PRIMARY KEY,
                customer_id INTEGER,
                document_type VARCHAR(100),
                document_number VARCHAR(100),
                issuing_country CHAR(3),
                issue_date DATE,
                expiry_date DATE,
                verification_status VARCHAR(30),
                verified_at TIMESTAMP,
                verified_by INTEGER
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.kyc_documents", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.kyc_documents table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/kyc_documents.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                String expiryDate = line[6].isEmpty() ? null : line[6];
                String verifiedAt = line[8].isEmpty() ? null : line[8];
                Integer verifiedBy = line[9].isEmpty() ? null : Integer.parseInt(line[9]);
                jdbcTemplate.update(
                    "INSERT INTO banking.kyc_documents (document_id, customer_id, document_type, document_number, issuing_country, issue_date, expiry_date, verification_status, verified_at, verified_by) VALUES (?, ?, ?, ?, ?, ?::date, ?::date, ?, ?::timestamp, ?)",
                    Long.parseLong(line[0]), Integer.parseInt(line[1]), line[2], line[3],
                    line[4], line[5], expiryDate, line[7], verifiedAt, verifiedBy
                );
            }
        }
        log.info("Seeded banking.kyc_documents table with sample data");
    }

    private void createAndSeedInterestRates() throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS banking.interest_rates (
                rate_id BIGINT PRIMARY KEY,
                product_type VARCHAR(50),
                term_months INTEGER,
                rate DECIMAL(6,4),
                apy DECIMAL(6,4),
                effective_date DATE,
                expiry_date DATE,
                min_balance DECIMAL(10,2),
                tier VARCHAR(20)
            )
        """);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking.interest_rates", Integer.class);
        if (count != null && count > 0) {
            log.info("banking.interest_rates table already has data, skipping seed");
            return;
        }

        try (var reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("sample-data/interest_rates.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                Integer termMonths = line[2].isEmpty() ? null : Integer.parseInt(line[2]);
                String expiryDate = line[6].isEmpty() ? null : line[6];
                jdbcTemplate.update(
                    "INSERT INTO banking.interest_rates (rate_id, product_type, term_months, rate, apy, effective_date, expiry_date, min_balance, tier) VALUES (?, ?, ?, ?, ?, ?::date, ?::date, ?, ?)",
                    Long.parseLong(line[0]), line[1], termMonths,
                    new BigDecimal(line[3]), new BigDecimal(line[4]),
                    line[5], expiryDate, new BigDecimal(line[7]), line[8]
                );
            }
        }
        log.info("Seeded banking.interest_rates table with sample data");
    }
}
