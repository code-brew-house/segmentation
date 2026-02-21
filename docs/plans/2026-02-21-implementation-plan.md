# Segment Workflow System — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a full-stack DAG-based customer segmentation workflow system with Data Marts, 7 node types, Temporal orchestration, per-node execution metrics, and CSV output.

**Architecture:** Spring Boot 3.5 backend with Temporal for workflow orchestration. Postgres stores the DAG graph, data mart catalog, execution metrics, and CTAS result tables. Next.js 16 frontend with React Flow canvas, shadcn/ui, and dagre layout. Frontend is a thin REST client.

**Tech Stack:** Java 21, Spring Boot 3.5, Temporal SDK, PostgreSQL 16, Next.js 16, React Flow v12, Tailwind CSS v4, shadcn/ui, dagre

---

## Phase 1: Backend Infrastructure

### Task 1: Docker Compose + Dependencies

**Files:**
- Create: `docker-compose.yml`
- Modify: `segment/build.gradle.kts`
- Modify: `segment/src/main/resources/application.properties`

**Step 1: Create docker-compose.yml**

```yaml
services:
  temporal:
    image: temporalio/auto-setup:latest
    ports:
      - "7233:7233"
    environment:
      - DB=postgres12
      - DB_PORT=5432
      - POSTGRES_USER=temporal
      - POSTGRES_PWD=temporal
      - POSTGRES_SEEDS=postgres
    depends_on:
      postgres:
        condition: service_healthy

  temporal-ui:
    image: temporalio/ui:latest
    ports:
      - "8088:8080"
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
    depends_on:
      - temporal

  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=segment
      - POSTGRES_USER=segment
      - POSTGRES_PASSWORD=segment
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U segment"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

**Step 2: Update build.gradle.kts with all dependencies**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.5.12-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.workflow"
version = "0.0.1-SNAPSHOT"
description = "Segment Management"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Temporal
    implementation("io.temporal:temporal-sdk:1.27.0")
    implementation("io.temporal:temporal-spring-boot-starter:1.27.0")

    // CSV
    implementation("com.opencsv:opencsv:5.9")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

**Step 3: Update application.properties**

```properties
spring.application.name=Segment

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/segment
spring.datasource.username=segment
spring.datasource.password=segment
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true

# Temporal
spring.temporal.workers-auto-discovery.packages=com.workflow.segment.temporal
spring.temporal.connection.target=localhost:7233
spring.temporal.namespace=default

# CORS
app.cors.allowed-origins=http://localhost:3000

# Data Mart seed file
app.data-mart.seed-file=classpath:data-mart-seed.json

# CSV output directory
app.output.directory=./outputs
```

**Step 4: Start Docker Compose, verify Postgres + Temporal are running**

Run: `docker compose up -d`
Run: `docker compose ps` — expect all 3 services healthy
Run: `docker compose logs temporal` — verify Temporal started

**Step 5: Verify Spring Boot starts**

Run: `cd segment && ./gradlew bootRun`
Expected: Application starts without errors (may warn about no Temporal worker yet)

**Step 6: Commit**

```bash
git add docker-compose.yml segment/build.gradle.kts segment/src/main/resources/application.properties
git commit -m "feat: add Docker Compose (Temporal + Postgres) and dependencies"
```

---

### Task 2: CORS Configuration

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/config/CorsConfig.java`

**Step 1: Create CORS config**

```java
package com.workflow.segment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins.split(","))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
```

**Step 2: Commit**

```bash
git add segment/src/main/java/com/workflow/segment/config/CorsConfig.java
git commit -m "feat: add CORS configuration for frontend"
```

---

## Phase 2: Data Mart Model & API

### Task 3: Data Mart Entity + Repository

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/model/DataMart.java`
- Create: `segment/src/main/java/com/workflow/segment/model/DataMartColumn.java`
- Create: `segment/src/main/java/com/workflow/segment/repository/DataMartRepository.java`
- Create: `segment/src/main/java/com/workflow/segment/repository/DataMartColumnRepository.java`
- Test: `segment/src/test/java/com/workflow/segment/repository/DataMartRepositoryTest.java`

**Step 1: Write the failing test**

```java
package com.workflow.segment.repository;

import com.workflow.segment.model.DataMart;
import com.workflow.segment.model.DataMartColumn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

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
```

Create `segment/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
```

**Step 2: Run test to verify it fails**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.repository.DataMartRepositoryTest"`
Expected: FAIL — classes not found

**Step 3: Create DataMart entity**

```java
package com.workflow.segment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "data_mart")
@Getter @Setter @NoArgsConstructor
public class DataMart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tableName;

    @Column(nullable = false)
    private String schemaName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "dataMart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinalPosition")
    private List<DataMartColumn> columns = new ArrayList<>();
}
```

**Step 4: Create DataMartColumn entity**

```java
package com.workflow.segment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "data_mart_column")
@Getter @Setter @NoArgsConstructor
public class DataMartColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_mart_id", nullable = false)
    @JsonIgnore
    private DataMart dataMart;

    @Column(nullable = false)
    private String columnName;

    @Column(nullable = false)
    private String dataType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer ordinalPosition;
}
```

**Step 5: Create repositories**

```java
package com.workflow.segment.repository;

import com.workflow.segment.model.DataMart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DataMartRepository extends JpaRepository<DataMart, UUID> {
    Optional<DataMart> findByTableName(String tableName);
}
```

```java
package com.workflow.segment.repository;

import com.workflow.segment.model.DataMartColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DataMartColumnRepository extends JpaRepository<DataMartColumn, UUID> {
}
```

**Step 6: Run test to verify it passes**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.repository.DataMartRepositoryTest"`
Expected: PASS

**Step 7: Commit**

```bash
git add segment/src/
git commit -m "feat: add DataMart and DataMartColumn entities with repositories"
```

---

### Task 4: Data Mart Seed Loader

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/config/DataMartSeeder.java`
- Create: `segment/src/main/resources/data-mart-seed.json`
- Test: `segment/src/test/java/com/workflow/segment/config/DataMartSeederTest.java`

**Step 1: Write the failing test**

```java
package com.workflow.segment.config;

import com.workflow.segment.model.DataMart;
import com.workflow.segment.repository.DataMartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DataMartSeederTest {

    @Autowired
    private DataMartRepository dataMartRepository;

    @Test
    void shouldLoadSeedDataOnStartup() {
        List<DataMart> dataMarts = dataMartRepository.findAll();
        assertThat(dataMarts).isNotEmpty();

        DataMart customers = dataMartRepository.findByTableName("customers").orElseThrow();
        assertThat(customers.getDescription()).isNotBlank();
        assertThat(customers.getColumns()).isNotEmpty();
        assertThat(customers.getColumns().get(0).getColumnName()).isNotBlank();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.config.DataMartSeederTest"`
Expected: FAIL — no seed data loaded

**Step 3: Create seed file**

```json
{
  "data_marts": [
    {
      "table_name": "customers",
      "schema_name": "public",
      "description": "Customer master data with demographics and contact information",
      "columns": [
        {"column_name": "customer_id", "data_type": "INTEGER", "description": "Unique customer identifier"},
        {"column_name": "name", "data_type": "VARCHAR(255)", "description": "Full name"},
        {"column_name": "email", "data_type": "VARCHAR(255)", "description": "Email address"},
        {"column_name": "city", "data_type": "VARCHAR(100)", "description": "City of residence"},
        {"column_name": "age", "data_type": "INTEGER", "description": "Age in years"},
        {"column_name": "created_at", "data_type": "TIMESTAMP", "description": "Account creation date"}
      ]
    },
    {
      "table_name": "purchases",
      "schema_name": "public",
      "description": "Purchase transaction history",
      "columns": [
        {"column_name": "purchase_id", "data_type": "INTEGER", "description": "Unique purchase identifier"},
        {"column_name": "customer_id", "data_type": "INTEGER", "description": "Foreign key to customers table"},
        {"column_name": "product_name", "data_type": "VARCHAR(255)", "description": "Name of purchased product"},
        {"column_name": "amount", "data_type": "DECIMAL(10,2)", "description": "Purchase amount"},
        {"column_name": "purchase_date", "data_type": "DATE", "description": "Date of purchase"},
        {"column_name": "category", "data_type": "VARCHAR(100)", "description": "Product category"}
      ]
    },
    {
      "table_name": "demographics",
      "schema_name": "public",
      "description": "Extended demographic data for customer enrichment",
      "columns": [
        {"column_name": "customer_id", "data_type": "INTEGER", "description": "Foreign key to customers table"},
        {"column_name": "income_bracket", "data_type": "VARCHAR(50)", "description": "Income range bracket"},
        {"column_name": "education_level", "data_type": "VARCHAR(50)", "description": "Highest education level"},
        {"column_name": "occupation", "data_type": "VARCHAR(100)", "description": "Current occupation"},
        {"column_name": "marital_status", "data_type": "VARCHAR(20)", "description": "Marital status"}
      ]
    }
  ]
}
```

**Step 4: Create DataMartSeeder**

```java
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
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
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
```

Also create `segment/src/test/resources/data-mart-seed.json` — copy the same seed file for tests. And update `application-test.properties` to add:
```properties
app.data-mart.seed-file=classpath:data-mart-seed.json
app.output.directory=./test-outputs
```

**Step 5: Run test to verify it passes**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.config.DataMartSeederTest"`
Expected: PASS

**Step 6: Commit**

```bash
git add segment/src/
git commit -m "feat: add DataMart seed loader with sample data"
```

---

### Task 5: Data Mart REST API

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/dto/DataMartResponse.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/DataMartColumnResponse.java`
- Create: `segment/src/main/java/com/workflow/segment/controller/DataMartController.java`
- Test: `segment/src/test/java/com/workflow/segment/controller/DataMartControllerTest.java`

**Step 1: Write the failing test**

```java
package com.workflow.segment.controller;

import com.workflow.segment.model.DataMart;
import com.workflow.segment.model.DataMartColumn;
import com.workflow.segment.repository.DataMartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
                .andExpect(jsonPath("$[0].columnCount").exists());
    }

    @Test
    void shouldGetDataMartById() throws Exception {
        // Seed data is loaded by DataMartSeeder, get first one
        mockMvc.perform(get("/api/data-marts"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    // Extract first ID from response and test detail endpoint
                });
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.controller.DataMartControllerTest"`
Expected: FAIL — 404, controller not found

**Step 3: Create DTOs**

```java
package com.workflow.segment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data @AllArgsConstructor
public class DataMartResponse {
    private UUID id;
    private String tableName;
    private String schemaName;
    private String description;
    private int columnCount;
}
```

```java
package com.workflow.segment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data @AllArgsConstructor
public class DataMartColumnResponse {
    private UUID id;
    private String columnName;
    private String dataType;
    private String description;
    private Integer ordinalPosition;
}
```

Create a detail response DTO:
```java
package com.workflow.segment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data @AllArgsConstructor
public class DataMartDetailResponse {
    private UUID id;
    private String tableName;
    private String schemaName;
    private String description;
    private List<DataMartColumnResponse> columns;
}
```

**Step 4: Create controller**

```java
package com.workflow.segment.controller;

import com.workflow.segment.dto.DataMartColumnResponse;
import com.workflow.segment.dto.DataMartDetailResponse;
import com.workflow.segment.dto.DataMartResponse;
import com.workflow.segment.repository.DataMartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/data-marts")
@RequiredArgsConstructor
public class DataMartController {

    private final DataMartRepository dataMartRepository;

    @GetMapping
    public List<DataMartResponse> listAll() {
        return dataMartRepository.findAll().stream()
                .map(dm -> new DataMartResponse(
                        dm.getId(),
                        dm.getTableName(),
                        dm.getSchemaName(),
                        dm.getDescription(),
                        dm.getColumns().size()
                ))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataMartDetailResponse> getById(@PathVariable UUID id) {
        return dataMartRepository.findById(id)
                .map(dm -> ResponseEntity.ok(new DataMartDetailResponse(
                        dm.getId(),
                        dm.getTableName(),
                        dm.getSchemaName(),
                        dm.getDescription(),
                        dm.getColumns().stream()
                                .map(col -> new DataMartColumnResponse(
                                        col.getId(),
                                        col.getColumnName(),
                                        col.getDataType(),
                                        col.getDescription(),
                                        col.getOrdinalPosition()
                                ))
                                .toList()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
```

**Step 5: Run test to verify it passes**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.controller.DataMartControllerTest"`
Expected: PASS

**Step 6: Commit**

```bash
git add segment/src/
git commit -m "feat: add Data Mart REST API (list + detail)"
```

---

## Phase 3: Workflow & Node Models

### Task 6: Workflow Entity + Repository + CRUD API

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/model/SegmentWorkflow.java`
- Create: `segment/src/main/java/com/workflow/segment/model/WorkflowStatus.java`
- Create: `segment/src/main/java/com/workflow/segment/repository/SegmentWorkflowRepository.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/CreateWorkflowRequest.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/WorkflowResponse.java`
- Create: `segment/src/main/java/com/workflow/segment/service/WorkflowService.java`
- Create: `segment/src/main/java/com/workflow/segment/controller/WorkflowController.java`
- Test: `segment/src/test/java/com/workflow/segment/controller/WorkflowControllerTest.java`

**Step 1: Write the failing test**

```java
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
        var request = new CreateWorkflowRequest("Test Workflow", "tester");

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Workflow"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldListWorkflows() throws Exception {
        // Create one first
        var request = new CreateWorkflowRequest("List Test", "tester");
        mockMvc.perform(post("/api/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetWorkflowById() throws Exception {
        var request = new CreateWorkflowRequest("Get Test", "tester");
        var result = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/workflows/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Get Test"))
                .andExpect(jsonPath("$.nodes").isArray());
    }

    @Test
    void shouldDeleteWorkflow() throws Exception {
        var request = new CreateWorkflowRequest("Delete Test", "tester");
        var result = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/workflows/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workflows/" + id))
                .andExpect(status().isNotFound());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.controller.WorkflowControllerTest"`
Expected: FAIL

**Step 3: Create enum, entity, DTO, repository, service, controller**

WorkflowStatus enum:
```java
package com.workflow.segment.model;

public enum WorkflowStatus {
    DRAFT, RUNNING, COMPLETED, FAILED
}
```

SegmentWorkflow entity:
```java
package com.workflow.segment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "segment_workflow")
@Getter @Setter @NoArgsConstructor
public class SegmentWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String createdBy;

    @CreationTimestamp
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SegmentWorkflowNode> nodes = new ArrayList<>();
}
```

CreateWorkflowRequest:
```java
package com.workflow.segment.dto;

public record CreateWorkflowRequest(String name, String createdBy) {}
```

WorkflowResponse:
```java
package com.workflow.segment.dto;

import com.workflow.segment.model.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
    UUID id,
    String name,
    String createdBy,
    Instant createdAt,
    WorkflowStatus status,
    int nodeCount
) {}
```

WorkflowDetailResponse (with nodes):
```java
package com.workflow.segment.dto;

import com.workflow.segment.model.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowDetailResponse(
    UUID id,
    String name,
    String createdBy,
    Instant createdAt,
    WorkflowStatus status,
    List<NodeResponse> nodes
) {}
```

Repository:
```java
package com.workflow.segment.repository;

import com.workflow.segment.model.SegmentWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SegmentWorkflowRepository extends JpaRepository<SegmentWorkflow, UUID> {}
```

Service:
```java
package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.SegmentWorkflow;
import com.workflow.segment.model.WorkflowStatus;
import com.workflow.segment.repository.SegmentWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final SegmentWorkflowRepository workflowRepository;

    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        SegmentWorkflow wf = new SegmentWorkflow();
        wf.setName(request.name());
        wf.setCreatedBy(request.createdBy());
        wf.setStatus(WorkflowStatus.DRAFT);
        wf = workflowRepository.save(wf);
        return toResponse(wf);
    }

    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkflowDetailResponse getWorkflow(UUID id) {
        SegmentWorkflow wf = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        return new WorkflowDetailResponse(
                wf.getId(), wf.getName(), wf.getCreatedBy(),
                wf.getCreatedAt(), wf.getStatus(),
                wf.getNodes().stream().map(this::toNodeResponse).toList()
        );
    }

    @Transactional
    public void deleteWorkflow(UUID id) {
        if (!workflowRepository.existsById(id)) {
            throw new WorkflowNotFoundException(id);
        }
        workflowRepository.deleteById(id);
    }

    private WorkflowResponse toResponse(SegmentWorkflow wf) {
        return new WorkflowResponse(
                wf.getId(), wf.getName(), wf.getCreatedBy(),
                wf.getCreatedAt(), wf.getStatus(), wf.getNodes().size()
        );
    }

    private NodeResponse toNodeResponse(com.workflow.segment.model.SegmentWorkflowNode node) {
        return new NodeResponse(
                node.getId(), node.getType(), node.getParentNodeIds(),
                node.getConfig(), node.getPosition()
        );
    }
}
```

Create `WorkflowNotFoundException`:
```java
package com.workflow.segment.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WorkflowNotFoundException extends RuntimeException {
    public WorkflowNotFoundException(UUID id) {
        super("Workflow not found: " + id);
    }
}
```

Controller:
```java
package com.workflow.segment.controller;

import com.workflow.segment.dto.*;
import com.workflow.segment.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse create(@RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(request);
    }

    @GetMapping
    public List<WorkflowResponse> list() {
        return workflowService.listWorkflows();
    }

    @GetMapping("/{id}")
    public WorkflowDetailResponse get(@PathVariable UUID id) {
        return workflowService.getWorkflow(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        workflowService.deleteWorkflow(id);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.controller.WorkflowControllerTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add segment/src/
git commit -m "feat: add Workflow CRUD API (create, list, get, delete)"
```

---

### Task 7: Node Entity + Node CRUD API

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/model/SegmentWorkflowNode.java`
- Create: `segment/src/main/java/com/workflow/segment/model/NodeType.java`
- Create: `segment/src/main/java/com/workflow/segment/repository/SegmentWorkflowNodeRepository.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/AddNodeRequest.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/UpdateNodeRequest.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/NodeResponse.java`
- Create: `segment/src/main/java/com/workflow/segment/service/NodeService.java`
- Create: `segment/src/main/java/com/workflow/segment/controller/NodeController.java`
- Test: `segment/src/test/java/com/workflow/segment/controller/NodeControllerTest.java`

**Step 1: Write the failing test**

```java
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

import java.util.List;
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
        var result = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWorkflowRequest("Node Test WF", "tester"))))
                .andReturn();
        workflowId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void shouldAddStartNode() throws Exception {
        var request = new AddNodeRequest(
                List.of(), "START_FILE_UPLOAD",
                Map.of("file_path", "/data/customers.csv"), null);

        mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("START_FILE_UPLOAD"));
    }

    @Test
    void shouldAddFilterNodeWithParent() throws Exception {
        // Add start node first
        var startReq = new AddNodeRequest(
                List.of(), "START_FILE_UPLOAD",
                Map.of("file_path", "/data/customers.csv"), null);
        var startResult = mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andReturn();
        String startNodeId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("id").asText();

        // Add filter node
        var filterReq = new AddNodeRequest(
                List.of(startNodeId), "FILTER",
                Map.of("data_mart_table", "purchases", "mode", "JOIN"), null);

        mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filterReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FILTER"))
                .andExpect(jsonPath("$.parentNodeIds[0]").value(startNodeId));
    }

    @Test
    void shouldUpdateNodeConfig() throws Exception {
        // Add node
        var startReq = new AddNodeRequest(
                List.of(), "START_QUERY",
                Map.of("raw_sql", "SELECT * FROM customers"), null);
        var result = mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andReturn();
        String nodeId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Update config
        var updateReq = new UpdateNodeRequest(
                null, Map.of("raw_sql", "SELECT * FROM customers WHERE active = true"));

        mockMvc.perform(put("/api/workflows/" + workflowId + "/nodes/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.raw_sql").value("SELECT * FROM customers WHERE active = true"));
    }

    @Test
    void shouldDeleteNode() throws Exception {
        var startReq = new AddNodeRequest(
                List.of(), "START_FILE_UPLOAD",
                Map.of("file_path", "/data/test.csv"), null);
        var result = mockMvc.perform(post("/api/workflows/" + workflowId + "/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andReturn();
        String nodeId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/workflows/" + workflowId + "/nodes/" + nodeId))
                .andExpect(status().isNoContent());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.controller.NodeControllerTest"`
Expected: FAIL

**Step 3: Create NodeType enum**

```java
package com.workflow.segment.model;

public enum NodeType {
    START_FILE_UPLOAD,
    START_QUERY,
    FILTER,
    ENRICH,
    SPLIT,
    JOIN,
    STOP
}
```

**Step 4: Create SegmentWorkflowNode entity**

```java
package com.workflow.segment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "segment_workflow_node")
@Getter @Setter @NoArgsConstructor
public class SegmentWorkflowNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    @JsonIgnore
    private SegmentWorkflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType type;

    @ElementCollection
    @CollectionTable(name = "node_parent_ids", joinColumns = @JoinColumn(name = "node_id"))
    @Column(name = "parent_node_id")
    private List<UUID> parentNodeIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config;

    private Integer position;
}
```

**Step 5: Create DTOs, repository, service, controller**

AddNodeRequest:
```java
package com.workflow.segment.dto;

import java.util.List;
import java.util.Map;

public record AddNodeRequest(
    List<String> parentNodeIds,
    String type,
    Map<String, Object> config,
    Integer position
) {}
```

UpdateNodeRequest:
```java
package com.workflow.segment.dto;

import java.util.List;
import java.util.Map;

public record UpdateNodeRequest(
    List<String> parentNodeIds,
    Map<String, Object> config
) {}
```

NodeResponse:
```java
package com.workflow.segment.dto;

import com.workflow.segment.model.NodeType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NodeResponse(
    UUID id,
    NodeType type,
    List<UUID> parentNodeIds,
    Map<String, Object> config,
    Integer position
) {}
```

Repository:
```java
package com.workflow.segment.repository;

import com.workflow.segment.model.SegmentWorkflowNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SegmentWorkflowNodeRepository extends JpaRepository<SegmentWorkflowNode, UUID> {
    List<SegmentWorkflowNode> findByWorkflowId(UUID workflowId);
}
```

NodeService:
```java
package com.workflow.segment.service;

import com.workflow.segment.dto.*;
import com.workflow.segment.model.*;
import com.workflow.segment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NodeService {

    private final SegmentWorkflowRepository workflowRepository;
    private final SegmentWorkflowNodeRepository nodeRepository;

    @Transactional
    public NodeResponse addNode(UUID workflowId, AddNodeRequest request) {
        SegmentWorkflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        SegmentWorkflowNode node = new SegmentWorkflowNode();
        node.setWorkflow(wf);
        node.setType(NodeType.valueOf(request.type()));
        node.setParentNodeIds(request.parentNodeIds() != null
                ? request.parentNodeIds().stream().map(UUID::fromString).toList()
                : List.of());
        node.setConfig(request.config());
        node.setPosition(request.position());

        node = nodeRepository.save(node);
        return toResponse(node);
    }

    @Transactional
    public NodeResponse updateNode(UUID workflowId, UUID nodeId, UpdateNodeRequest request) {
        SegmentWorkflowNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        if (request.parentNodeIds() != null) {
            node.setParentNodeIds(request.parentNodeIds().stream().map(UUID::fromString).toList());
        }
        if (request.config() != null) {
            node.setConfig(request.config());
        }

        node = nodeRepository.save(node);
        return toResponse(node);
    }

    @Transactional
    public void deleteNode(UUID workflowId, UUID nodeId) {
        // Delete node and all descendants
        deleteNodeAndDescendants(nodeId);
    }

    private void deleteNodeAndDescendants(UUID nodeId) {
        // Find children of this node
        List<SegmentWorkflowNode> allNodes = nodeRepository.findAll();
        List<SegmentWorkflowNode> children = allNodes.stream()
                .filter(n -> n.getParentNodeIds().contains(nodeId))
                .toList();

        for (SegmentWorkflowNode child : children) {
            deleteNodeAndDescendants(child.getId());
        }
        nodeRepository.deleteById(nodeId);
    }

    private NodeResponse toResponse(SegmentWorkflowNode node) {
        return new NodeResponse(
                node.getId(), node.getType(), node.getParentNodeIds(),
                node.getConfig(), node.getPosition()
        );
    }
}
```

Create `NodeNotFoundException`:
```java
package com.workflow.segment.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NodeNotFoundException extends RuntimeException {
    public NodeNotFoundException(UUID id) {
        super("Node not found: " + id);
    }
}
```

NodeController:
```java
package com.workflow.segment.controller;

import com.workflow.segment.dto.*;
import com.workflow.segment.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/workflows/{workflowId}/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NodeResponse addNode(@PathVariable UUID workflowId, @RequestBody AddNodeRequest request) {
        return nodeService.addNode(workflowId, request);
    }

    @PutMapping("/{nodeId}")
    public NodeResponse updateNode(@PathVariable UUID workflowId, @PathVariable UUID nodeId,
                                   @RequestBody UpdateNodeRequest request) {
        return nodeService.updateNode(workflowId, nodeId, request);
    }

    @DeleteMapping("/{nodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNode(@PathVariable UUID workflowId, @PathVariable UUID nodeId) {
        nodeService.deleteNode(workflowId, nodeId);
    }
}
```

**Step 6: Run tests**

Run: `cd segment && ./gradlew test --tests "com.workflow.segment.controller.NodeControllerTest"`
Expected: PASS

**Step 7: Commit**

```bash
git add segment/src/
git commit -m "feat: add Node CRUD API (add, update, delete)"
```

---

## Phase 4: Execution Infrastructure

### Task 8: Workflow Execution Entity + API

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/model/WorkflowExecution.java`
- Create: `segment/src/main/java/com/workflow/segment/model/NodeExecutionResult.java`
- Create: `segment/src/main/java/com/workflow/segment/model/ExecutionStatus.java`
- Create: `segment/src/main/java/com/workflow/segment/repository/WorkflowExecutionRepository.java`
- Create: `segment/src/main/java/com/workflow/segment/repository/NodeExecutionResultRepository.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/ExecutionResponse.java`
- Create: `segment/src/main/java/com/workflow/segment/dto/NodeExecutionResultResponse.java`
- Create: `segment/src/main/java/com/workflow/segment/service/ExecutionService.java`
- Modify: `segment/src/main/java/com/workflow/segment/controller/WorkflowController.java`
- Test: `segment/src/test/java/com/workflow/segment/controller/ExecutionControllerTest.java`

**Step 1: Write the failing test for execution status endpoint**

```java
package com.workflow.segment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.segment.dto.CreateWorkflowRequest;
import com.workflow.segment.model.*;
import com.workflow.segment.repository.*;
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
    @Autowired private SegmentWorkflowRepository workflowRepo;
    @Autowired private WorkflowExecutionRepository executionRepo;

    private String workflowId;

    @BeforeEach
    void setUp() throws Exception {
        var result = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWorkflowRequest("Exec Test WF", "tester"))))
                .andReturn();
        workflowId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void shouldListExecutions() throws Exception {
        mockMvc.perform(get("/api/workflows/" + workflowId + "/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
```

**Step 2: Run test — expect FAIL**

**Step 3: Create entities, repos, DTOs, service, and add execution endpoints to controller**

ExecutionStatus:
```java
package com.workflow.segment.model;

public enum ExecutionStatus {
    PENDING, RUNNING, SUCCESS, FAILED
}
```

WorkflowExecution:
```java
package com.workflow.segment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow_execution")
@Getter @Setter @NoArgsConstructor
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private SegmentWorkflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    private Instant startedAt;
    private Instant completedAt;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NodeExecutionResult> nodeResults = new ArrayList<>();
}
```

NodeExecutionResult:
```java
package com.workflow.segment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_execution_result")
@Getter @Setter @NoArgsConstructor
public class NodeExecutionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private WorkflowExecution execution;

    @Column(nullable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeType;

    private Integer inputRecordCount;
    private Integer filteredRecordCount;
    private Integer outputRecordCount;
    private String resultTableName;
    private String outputFilePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Instant startedAt;
    private Instant completedAt;
}
```

Repositories:
```java
package com.workflow.segment.repository;

import com.workflow.segment.model.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID> {
    List<WorkflowExecution> findByWorkflowIdOrderByStartedAtDesc(UUID workflowId);
}
```

```java
package com.workflow.segment.repository;

import com.workflow.segment.model.NodeExecutionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeExecutionResultRepository extends JpaRepository<NodeExecutionResult, UUID> {
    List<NodeExecutionResult> findByExecutionId(UUID executionId);
    Optional<NodeExecutionResult> findByExecutionIdAndNodeId(UUID executionId, UUID nodeId);
}
```

DTOs, service, and controller endpoints for listing executions and getting execution details. Add to `WorkflowController`:

```java
@GetMapping("/{id}/executions")
public List<ExecutionResponse> listExecutions(@PathVariable UUID id) {
    return executionService.listExecutions(id);
}

@GetMapping("/{id}/executions/{execId}")
public ExecutionDetailResponse getExecution(@PathVariable UUID id, @PathVariable UUID execId) {
    return executionService.getExecution(execId);
}
```

**Step 4: Run test — expect PASS**

**Step 5: Commit**

```bash
git add segment/src/
git commit -m "feat: add WorkflowExecution and NodeExecutionResult entities with API"
```

---

### Task 9: Temporal Activities — FileUpload + StartQuery

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/temporal/activities/SegmentActivities.java`
- Create: `segment/src/main/java/com/workflow/segment/temporal/activities/SegmentActivitiesImpl.java`
- Create: `segment/src/main/java/com/workflow/segment/temporal/model/` (activity input/output DTOs)
- Test: `segment/src/test/java/com/workflow/segment/temporal/activities/SegmentActivitiesImplTest.java`

**Step 1: Write test for fileUploadActivity**

Test that it reads a CSV and creates a table in the database. Use H2 for tests.

```java
@Test
void fileUploadActivity_shouldCreateTableFromCsv() {
    // Create a temp CSV file
    // Call fileUploadActivity with the path
    // Verify the table was created and row count is correct
}
```

**Step 2: Run test — expect FAIL**

**Step 3: Implement activity interface + implementation**

Activities interface:
```java
package com.workflow.segment.temporal.activities;

import com.workflow.segment.temporal.model.*;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SegmentActivities {

    @ActivityMethod
    FileUploadResult fileUploadActivity(FileUploadInput input);

    @ActivityMethod
    StartQueryResult startQueryActivity(StartQueryInput input);

    @ActivityMethod
    FilterResult filterActivity(FilterInput input);

    @ActivityMethod
    EnrichResult enrichActivity(EnrichInput input);

    @ActivityMethod
    StopNodeResult stopNodeActivity(StopNodeInput input);
}
```

Implement `fileUploadActivity`:
- Read CSV using OpenCSV
- `DROP TABLE IF EXISTS targetTable`
- Infer column types from CSV headers (all VARCHAR for POC)
- `CREATE TABLE targetTable (col1 VARCHAR, col2 VARCHAR, ...)`
- Bulk insert rows via JDBC batch
- Return `{resultTable, rowCount, columns}`

Implement `startQueryActivity`:
- `DROP TABLE IF EXISTS targetTable`
- `CREATE TABLE targetTable AS <raw_sql>`
- Count rows: `SELECT COUNT(*) FROM targetTable`
- Return `{resultTable, rowCount, columns}`

**Step 4: Run test — expect PASS**

**Step 5: Commit**

```bash
git add segment/src/
git commit -m "feat: add Temporal activities for FileUpload and StartQuery"
```

---

### Task 10: Temporal Activities — Filter + Enrich + Stop

**Files:**
- Modify: `segment/src/main/java/com/workflow/segment/temporal/activities/SegmentActivitiesImpl.java`
- Test: `segment/src/test/java/com/workflow/segment/temporal/activities/FilterEnrichStopTest.java`

**Step 1: Write tests for filterActivity (JOIN mode and SUBQUERY mode)**

```java
@Test
void filterActivity_joinMode_shouldFilterWithJoin() {
    // Set up source table and data mart table
    // Call filterActivity with JOIN mode
    // Verify CTAS table created, counts correct
}

@Test
void filterActivity_subqueryMode_shouldFilterWithSubquery() {
    // Same but with SUBQUERY mode
}
```

**Step 2: Run tests — expect FAIL**

**Step 3: Implement filterActivity**

JOIN mode SQL:
```sql
CREATE TABLE target AS
SELECT source.* FROM source_table source
JOIN data_mart_table dm ON source.join_key = dm.join_key
WHERE <conditions>
```

SUBQUERY mode SQL:
```sql
CREATE TABLE target AS
SELECT * FROM source_table
WHERE join_key IN (SELECT join_key FROM data_mart_table WHERE <conditions>)
```

Conditions use the recursive AND/OR group builder from the design:
- Leaf: `field operator value`
- Group: `(child1 AND/OR child2 AND/OR ...)`

**Step 4: Implement enrichActivity**

ADD_COLUMNS mode:
```sql
CREATE TABLE target AS
SELECT source.*, dm.col1, dm.col2
FROM source_table source
LEFT JOIN data_mart_table dm ON source.join_key = dm.join_key
```

ADD_RECORDS mode:
```sql
CREATE TABLE target AS
SELECT * FROM source_table
UNION ALL
SELECT <matched_columns> FROM data_mart_table
```

**Step 5: Implement stopNodeActivity**

```java
// Read all rows from source table
// Write to CSV file using OpenCSV
// Return {filePath, rowCount}
```

**Step 6: Run all tests — expect PASS**

**Step 7: Commit**

```bash
git add segment/src/
git commit -m "feat: add Temporal activities for Filter, Enrich, and Stop nodes"
```

---

### Task 11: SQL Condition Builder (Recursive AND/OR)

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/service/SqlConditionBuilder.java`
- Test: `segment/src/test/java/com/workflow/segment/service/SqlConditionBuilderTest.java`

**Step 1: Write tests for each condition type**

```java
@Test void shouldBuildSimpleEquals() { ... }
@Test void shouldBuildInClause() { ... }
@Test void shouldBuildBetween() { ... }
@Test void shouldBuildIsNull() { ... }
@Test void shouldBuildNestedAndOr() { ... }
@Test void shouldBuildDeeplyNested() { ... }
```

**Step 2: Run — expect FAIL**

**Step 3: Implement recursive SQL builder**

```java
package com.workflow.segment.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqlConditionBuilder {

    public static String buildWhereClause(Map<String, Object> config) {
        if (config == null || config.isEmpty()) return "";
        return "WHERE " + buildGroup(config);
    }

    private static String buildGroup(Map<String, Object> group) {
        String operation = (String) group.getOrDefault("operation", "AND");
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) group.get("conditions");

        if (conditions == null || conditions.isEmpty()) return "1=1";

        return conditions.stream()
                .map(cond -> {
                    if (cond.containsKey("conditions")) {
                        return "(" + buildGroup(cond) + ")";
                    } else {
                        return buildLeaf(cond);
                    }
                })
                .collect(Collectors.joining(" " + operation + " "));
    }

    private static String buildLeaf(Map<String, Object> cond) {
        String field = (String) cond.get("field");
        String operator = (String) cond.get("operator");
        Object value = cond.get("value");

        return switch (operator.toUpperCase()) {
            case "IS NULL" -> field + " IS NULL";
            case "IS NOT NULL" -> field + " IS NOT NULL";
            case "IN", "NOT IN" -> {
                List<String> values = (List<String>) value;
                String list = values.stream()
                        .map(v -> "'" + v.replace("'", "''") + "'")
                        .collect(Collectors.joining(", "));
                yield field + " " + operator + " (" + list + ")";
            }
            case "BETWEEN" -> {
                List<String> range = (List<String>) value;
                yield field + " BETWEEN " + quote(range.get(0)) + " AND " + quote(range.get(1));
            }
            default -> field + " " + operator + " " + quote((String) value);
        };
    }

    private static String quote(String value) {
        // Try to parse as number — don't quote numbers
        try { Double.parseDouble(value); return value; }
        catch (NumberFormatException e) { return "'" + value.replace("'", "''") + "'"; }
    }
}
```

**Step 4: Run — expect PASS**

**Step 5: Commit**

```bash
git add segment/src/
git commit -m "feat: add recursive SQL condition builder for AND/OR groups"
```

---

### Task 12: Temporal Workflows — Preview + Full Execution

**Files:**
- Create: `segment/src/main/java/com/workflow/segment/temporal/workflows/PreviewWorkflow.java`
- Create: `segment/src/main/java/com/workflow/segment/temporal/workflows/PreviewWorkflowImpl.java`
- Create: `segment/src/main/java/com/workflow/segment/temporal/workflows/FullExecutionWorkflow.java`
- Create: `segment/src/main/java/com/workflow/segment/temporal/workflows/FullExecutionWorkflowImpl.java`
- Create: `segment/src/main/java/com/workflow/segment/temporal/config/TemporalConfig.java`
- Test: `segment/src/test/java/com/workflow/segment/temporal/workflows/PreviewWorkflowTest.java`

**Step 1: Write test for PreviewWorkflow using Temporal test environment**

```java
@Test
void previewWorkflow_shouldExecuteSingleNode() {
    // Use Temporal TestWorkflowEnvironment
    // Register PreviewWorkflow + mock activities
    // Start workflow with a FILE_UPLOAD node
    // Verify activity was called and result returned
}
```

**Step 2: Run — expect FAIL**

**Step 3: Implement PreviewWorkflow**

```java
@WorkflowInterface
public interface PreviewWorkflow {
    @WorkflowMethod
    PreviewResult execute(PreviewInput input);
}
```

Implementation dispatches to the right activity based on node type.

**Step 4: Implement FullExecutionWorkflow**

```java
@WorkflowInterface
public interface FullExecutionWorkflow {
    @WorkflowMethod
    FullExecutionResult execute(FullExecutionInput input);
}
```

Implementation:
1. Build adjacency map from input graph
2. Find root nodes (no parents)
3. Walk graph: for each node, wait for all parents → execute activity → store result
4. Use `Async.function()` for parallel branches at SPLIT nodes
5. Use `Promise.allOf()` at JOIN nodes
6. Return combined results

**Step 5: Create TemporalConfig for worker registration**

```java
package com.workflow.segment.temporal.config;

import com.workflow.segment.temporal.activities.SegmentActivities;
import com.workflow.segment.temporal.workflows.FullExecutionWorkflowImpl;
import com.workflow.segment.temporal.workflows.PreviewWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "segment-workflow-queue";

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client, SegmentActivities activities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                PreviewWorkflowImpl.class,
                FullExecutionWorkflowImpl.class
        );
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
```

**Step 6: Run tests — expect PASS**

**Step 7: Commit**

```bash
git add segment/src/
git commit -m "feat: add Temporal Preview and FullExecution workflows"
```

---

### Task 13: Execution Trigger + Results + Download APIs

**Files:**
- Modify: `segment/src/main/java/com/workflow/segment/service/ExecutionService.java`
- Modify: `segment/src/main/java/com/workflow/segment/controller/WorkflowController.java`
- Create: `segment/src/main/java/com/workflow/segment/controller/ExecutionController.java`
- Test: integration test (requires Temporal — test with mocks)

**Step 1: Write test for execution trigger**

```java
@Test
void shouldTriggerWorkflowExecution() throws Exception {
    // Create workflow with nodes
    // POST /api/workflows/{id}/execute
    // Verify execution instance created with PENDING status
}
```

**Step 2: Run — expect FAIL**

**Step 3: Implement ExecutionService.executeWorkflow()**

```java
public ExecutionResponse executeWorkflow(UUID workflowId) {
    SegmentWorkflow wf = workflowRepository.findById(workflowId).orElseThrow();

    // Create execution instance
    WorkflowExecution execution = new WorkflowExecution();
    execution.setWorkflow(wf);
    execution.setStatus(ExecutionStatus.RUNNING);
    execution.setStartedAt(Instant.now());
    execution = executionRepository.save(execution);

    // Build graph input for Temporal
    List<SegmentWorkflowNode> nodes = nodeRepository.findByWorkflowId(workflowId);
    // ... convert to FullExecutionInput

    // Start Temporal workflow async
    FullExecutionWorkflow workflow = workflowClient.newWorkflowStub(
            FullExecutionWorkflow.class,
            WorkflowOptions.newBuilder()
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowId("exec-" + execution.getId())
                    .build()
    );
    WorkflowClient.start(workflow::execute, input);

    return toResponse(execution);
}
```

**Step 4: Implement node results endpoint**

```java
@GetMapping("/{id}/executions/{execId}/nodes/{nodeId}/results")
public ResponseEntity<List<Map<String, Object>>> getNodeResults(
        @PathVariable UUID id, @PathVariable UUID execId, @PathVariable UUID nodeId) {
    // Find NodeExecutionResult for this node
    // Query the result table: SELECT * FROM resultTableName LIMIT 100
    // Return as list of maps
}
```

**Step 5: Implement CSV download endpoint**

```java
@GetMapping("/{id}/executions/{execId}/nodes/{nodeId}/download")
public ResponseEntity<Resource> downloadCsv(
        @PathVariable UUID id, @PathVariable UUID execId, @PathVariable UUID nodeId) {
    // Find NodeExecutionResult for this node (must be STOP type)
    // Return the CSV file as a download
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(new FileSystemResource(filePath));
}
```

**Step 6: Implement preview endpoint**

```java
@PostMapping("/{id}/nodes/{nodeId}/preview")
public PreviewResponse previewNode(@PathVariable UUID id, @PathVariable UUID nodeId) {
    // Create temporary execution instance
    // Start PreviewWorkflow synchronously
    // Return result with sample rows
}
```

**Step 7: Run tests — expect PASS**

**Step 8: Commit**

```bash
git add segment/src/
git commit -m "feat: add execution trigger, results, preview, and CSV download APIs"
```

---

## Phase 5: Frontend

### Task 14: Next.js Project Setup

**Files:**
- Create: `frontend/` — entire Next.js project

**Step 1: Initialize Next.js project**

```bash
cd /Users/tushar/Desktop/Segment
npx create-next-app@latest frontend --typescript --tailwind --eslint --app --src-dir=false --import-alias="@/*"
```

**Step 2: Install dependencies**

```bash
cd frontend
npm install @xyflow/react dagre lucide-react
npm install -D @types/dagre
npx shadcn@latest init -d
npx shadcn@latest add button card dialog input label select table badge textarea tabs separator dropdown-menu scroll-area toggle-group tooltip
```

**Step 3: Create API client**

Create `frontend/lib/api.ts`:
```typescript
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

async function fetchApi<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  if (!res.ok) throw new Error(`API error: ${res.status}`);
  if (res.status === 204) return undefined as T;
  return res.json();
}

export const api = {
  // Data Marts
  listDataMarts: () => fetchApi<DataMart[]>('/data-marts'),
  getDataMart: (id: string) => fetchApi<DataMartDetail>(`/data-marts/${id}`),

  // Workflows
  listWorkflows: () => fetchApi<Workflow[]>('/workflows'),
  createWorkflow: (data: CreateWorkflowRequest) =>
    fetchApi<Workflow>('/workflows', { method: 'POST', body: JSON.stringify(data) }),
  getWorkflow: (id: string) => fetchApi<WorkflowDetail>(`/workflows/${id}`),
  deleteWorkflow: (id: string) => fetchApi<void>(`/workflows/${id}`, { method: 'DELETE' }),

  // Nodes
  addNode: (workflowId: string, data: AddNodeRequest) =>
    fetchApi<NodeResponse>(`/workflows/${workflowId}/nodes`, { method: 'POST', body: JSON.stringify(data) }),
  updateNode: (workflowId: string, nodeId: string, data: UpdateNodeRequest) =>
    fetchApi<NodeResponse>(`/workflows/${workflowId}/nodes/${nodeId}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteNode: (workflowId: string, nodeId: string) =>
    fetchApi<void>(`/workflows/${workflowId}/nodes/${nodeId}`, { method: 'DELETE' }),

  // Execution
  executeWorkflow: (workflowId: string) =>
    fetchApi<ExecutionResponse>(`/workflows/${workflowId}/execute`, { method: 'POST' }),
  listExecutions: (workflowId: string) =>
    fetchApi<ExecutionResponse[]>(`/workflows/${workflowId}/executions`),
  getExecution: (workflowId: string, execId: string) =>
    fetchApi<ExecutionDetail>(`/workflows/${workflowId}/executions/${execId}`),
  getNodeResults: (workflowId: string, execId: string, nodeId: string) =>
    fetchApi<Record<string, unknown>[]>(`/workflows/${workflowId}/executions/${execId}/nodes/${nodeId}/results`),
  downloadCsv: (workflowId: string, execId: string, nodeId: string) =>
    `${API_BASE}/workflows/${workflowId}/executions/${execId}/nodes/${nodeId}/download`,

  // Preview
  previewNode: (workflowId: string, nodeId: string) =>
    fetchApi<PreviewResponse>(`/workflows/${workflowId}/nodes/${nodeId}/preview`, { method: 'POST' }),
};
```

**Step 4: Create types**

Create `frontend/lib/types.ts` with all TypeScript interfaces matching backend DTOs.

**Step 5: Verify dev server starts**

Run: `cd frontend && npm run dev`
Expected: Next.js starts on port 3000

**Step 6: Commit**

```bash
git add frontend/
git commit -m "feat: initialize Next.js frontend with deps, API client, and types"
```

---

### Task 15: Layout + Navbar

**Files:**
- Modify: `frontend/app/layout.tsx`
- Create: `frontend/components/layout/navbar.tsx`

**Step 1: Create Navbar component**

```tsx
// Navbar with "Workflows" and "Data Marts" tabs
// Uses Next.js Link for navigation
// Highlights active tab based on current route
```

**Step 2: Update root layout**

Add Navbar to root layout so it appears on all pages.

**Step 3: Verify visually**

Run: `npm run dev` — check navbar renders with two tabs.

**Step 4: Commit**

```bash
git add frontend/
git commit -m "feat: add navbar with Workflows and Data Marts tabs"
```

---

### Task 16: Dashboard Page

**Files:**
- Modify: `frontend/app/page.tsx`
- Create: `frontend/components/dashboard/workflow-card.tsx`
- Create: `frontend/components/dashboard/create-workflow-dialog.tsx`

**Step 1: Build dashboard page**

- Fetch workflows from `api.listWorkflows()`
- Render card grid
- Each card: name, created by, created date, status badge, node count
- Delete button with confirmation
- "New Workflow" button → dialog → create → navigate

**Step 2: Build create workflow dialog**

shadcn Dialog with name + createdBy inputs.

**Step 3: Build workflow card component**

Card with actions.

**Step 4: Test visually with dev server**

**Step 5: Commit**

```bash
git add frontend/
git commit -m "feat: add workflow dashboard with create/delete"
```

---

### Task 17: Data Marts Pages

**Files:**
- Create: `frontend/app/data-marts/page.tsx`
- Create: `frontend/app/data-marts/[id]/page.tsx`
- Create: `frontend/components/data-marts/data-mart-card.tsx`
- Create: `frontend/components/data-marts/column-table.tsx`

**Step 1: Build data marts list page**

- Fetch from `api.listDataMarts()`
- Card grid: table name, description, column count
- Click card → navigate to detail page

**Step 2: Build data mart detail page**

- Fetch from `api.getDataMart(id)`
- Show table name, description
- shadcn Table listing columns: Name, Data Type, Description

**Step 3: Verify visually**

**Step 4: Commit**

```bash
git add frontend/
git commit -m "feat: add Data Marts list and detail pages"
```

---

### Task 18: Canvas Editor — React Flow Setup

**Files:**
- Create: `frontend/app/workflow/[id]/page.tsx`
- Create: `frontend/components/canvas/workflow-canvas.tsx`
- Create: `frontend/components/canvas/node-palette.tsx`

**Step 1: Create canvas editor page**

Three-panel layout:
- Node palette (left)
- React Flow canvas (center)
- Side panel (right, hidden by default)
- Console (bottom, collapsed by default)

**Step 2: Create WorkflowCanvas component**

```tsx
// React Flow wrapper
// Fetches workflow graph from API
// Converts nodes to React Flow format
// Uses dagre for auto-layout
// Handles drag-to-add from palette
// Handles edge creation/deletion
```

**Step 3: Create NodePalette component**

```tsx
// Grouped draggable items:
// Start: File Upload, Query
// Transform: Filter, Enrich
// Flow: Split, Join
// Terminal: Stop
```

**Step 4: Verify canvas renders with basic nodes**

**Step 5: Commit**

```bash
git add frontend/
git commit -m "feat: add canvas editor with React Flow and node palette"
```

---

### Task 19: Custom Node Components (7 types)

**Files:**
- Create: `frontend/components/canvas/nodes/start-file-upload-node.tsx`
- Create: `frontend/components/canvas/nodes/start-query-node.tsx`
- Create: `frontend/components/canvas/nodes/filter-node.tsx`
- Create: `frontend/components/canvas/nodes/enrich-node.tsx`
- Create: `frontend/components/canvas/nodes/split-node.tsx`
- Create: `frontend/components/canvas/nodes/join-node.tsx`
- Create: `frontend/components/canvas/nodes/stop-node.tsx`

**Step 1: Create base custom node structure**

Each node renders:
- Icon (from lucide-react)
- Label (type name)
- Config summary (e.g., file name, table name, condition count)
- Status badge (idle/running/success/failed)
- Post-execution metrics (input/output/filtered counts)
- Input/output handles for edges

**Step 2: Implement all 7 node types**

Each with appropriate icon, label, and config display.

**Step 3: Register node types in React Flow**

**Step 4: Verify all node types render on canvas**

**Step 5: Commit**

```bash
git add frontend/
git commit -m "feat: add 7 custom node components for React Flow canvas"
```

---

### Task 20: Side Panel + Node Configuration

**Files:**
- Create: `frontend/components/panel/side-panel.tsx`
- Create: `frontend/components/panel/file-upload-config.tsx`
- Create: `frontend/components/panel/query-config.tsx`
- Create: `frontend/components/panel/filter-config.tsx`
- Create: `frontend/components/panel/enrich-config.tsx`
- Create: `frontend/components/panel/stop-config.tsx`
- Create: `frontend/components/panel/condition-builder.tsx`

**Step 1: Create side panel shell**

Slides in from right when a node is selected. Shows appropriate config form based on node type. Close button.

**Step 2: Create file upload config**

File path text input + Preview button.

**Step 3: Create query config**

SQL textarea + Preview button.

**Step 4: Create filter config**

- Data mart table dropdown (fetches from API)
- Mode toggle (JOIN/Subquery)
- Join key input
- Condition builder component (recursive AND/OR)
- Preview button

**Step 5: Create condition builder component**

Recursive component that renders:
- Group header with AND/OR toggle
- Condition rows: field dropdown, operator dropdown, value input
- Add condition / Add group / Remove buttons
- Supports up to 3 levels of nesting

**Step 6: Create enrich config**

- Data mart table dropdown
- Mode toggle (Add Columns / Add Records)
- Conditional inputs based on mode
- Preview button

**Step 7: Create stop config**

- File name input
- Download button (visible after execution)

**Step 8: Wire auto-save on config changes**

Debounced `PUT /api/workflows/{id}/nodes/{nodeId}` on config edit.

**Step 9: Verify all config panels work**

**Step 10: Commit**

```bash
git add frontend/
git commit -m "feat: add side panel with config forms for all node types"
```

---

### Task 21: Console Panel + Execution Flow

**Files:**
- Create: `frontend/components/console/execution-console.tsx`
- Create: `frontend/components/console/result-table.tsx`
- Create: `frontend/components/console/metrics-table.tsx`

**Step 1: Create console panel**

Collapsible bottom panel. Shows:
- Execution log entries with timestamps
- Result data table (sample rows)
- Combined metrics table (after full execution)

**Step 2: Create result table component**

shadcn Table displaying arbitrary column/row data from API.

**Step 3: Create metrics table component**

Shows: Node, Type, Input Records, Filtered, Output Records, Status for all nodes.
Stop nodes show download link.

**Step 4: Wire preview flow**

- Click Preview on any node config → call `api.previewNode()`
- Console auto-expands
- Show log entry + spinner → results

**Step 5: Wire full execution flow**

- Click "Execute All" toolbar button → call `api.executeWorkflow()`
- Console auto-expands, badges reset
- Poll `api.getExecution()` for status updates
- On completion: update all node badges + show metrics table

**Step 6: Wire node result viewing**

Click node in metrics table → call `api.getNodeResults()` → show in result table.

**Step 7: Wire CSV download**

Stop node download button → open `api.downloadCsv()` URL in new tab.

**Step 8: Verify complete execution flow**

**Step 9: Commit**

```bash
git add frontend/
git commit -m "feat: add console panel with execution, metrics, and CSV download"
```

---

## Phase 6: Integration & Polish

### Task 22: End-to-End Integration Test

**Step 1: Start all services**

```bash
docker compose up -d
cd segment && ./gradlew bootRun &
cd frontend && npm run dev &
```

**Step 2: Manual E2E walkthrough**

1. Open `http://localhost:3000`
2. Navigate to Data Marts tab → verify seed data shows
3. Click a table → verify columns display
4. Go to Workflows tab → Create new workflow
5. Open workflow → drag File Upload node → configure file path
6. Preview file upload node → verify sample data in console
7. Add Filter node → select data mart table → build conditions → preview
8. Add Enrich node → test both modes
9. Add Split → two branches → Join
10. Add Stop nodes to branch ends
11. Execute full workflow
12. Verify metrics table shows all node counts
13. Download CSV from stop nodes

**Step 3: Fix any integration issues discovered**

**Step 4: Commit any fixes**

```bash
git add .
git commit -m "fix: integration issues from E2E testing"
```

---

### Task 23: Sample Data Setup

**Files:**
- Create: `segment/src/main/resources/sample-data/customers.csv`
- Create: `segment/src/main/java/com/workflow/segment/config/SampleDataSeeder.java`

**Step 1: Create sample CSV files**

Create `customers.csv` with ~100 rows of sample customer data.

**Step 2: Create sample data seeder**

On startup (after DataMartSeeder), create the actual data mart tables in Postgres with sample data so that filter/enrich operations have real tables to work against.

```java
// CREATE TABLE IF NOT EXISTS customers (customer_id INT, name VARCHAR, ...)
// INSERT sample rows
// Same for purchases, demographics tables
```

**Step 3: Verify seed data loads**

**Step 4: Commit**

```bash
git add segment/src/
git commit -m "feat: add sample data seeder for data mart tables"
```

---

### Task 24: Error Handling & Edge Cases

**Step 1: Add global exception handler**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Handle WorkflowNotFoundException, NodeNotFoundException
    // Handle validation errors
    // Handle Temporal execution errors
    // Return consistent error response format
}
```

**Step 2: Add frontend error handling**

- Toast notifications for API errors
- Error states on workflow cards
- Failed node highlighting on canvas

**Step 3: Commit**

```bash
git add .
git commit -m "feat: add error handling for backend and frontend"
```

---

## Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| 1 | 1-2 | Infrastructure (Docker, deps, CORS) |
| 2 | 3-5 | Data Mart model + API |
| 3 | 6-7 | Workflow + Node CRUD |
| 4 | 8-13 | Temporal activities, workflows, execution APIs |
| 5 | 14-21 | Complete Next.js frontend |
| 6 | 22-24 | Integration, sample data, error handling |

**Total: 24 tasks across 6 phases**

Backend tasks (1-13) should be completed before frontend tasks (14-21) since the frontend depends on the REST API. Tasks within each phase are sequential. Sample data (Task 23) can be done in parallel with frontend work.
