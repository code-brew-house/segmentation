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
