# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot application for Segment Management. Early-stage project using Spring Boot 3.5.x (snapshot), Java 21, and Gradle with Kotlin DSL.

- **Group**: `com.workflow`
- **Package**: `com.workflow.segment`
- **Entry point**: `SegmentApplication.java`

## Build & Development Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.workflow.segment.SegmentApplicationTests"

# Run a single test method
./gradlew test --tests "com.workflow.segment.SegmentApplicationTests.contextLoads"

# Clean build
./gradlew clean build

# Build OCI container image
./gradlew bootBuildImage
```

## Architecture

- **Build**: Gradle 8.x with Kotlin DSL (`build.gradle.kts`), Java toolchain targets JDK 21
- **Framework**: Spring Boot 3.5.12-SNAPSHOT with Spring Dependency Management plugin
- **Testing**: JUnit 5 via `spring-boot-starter-test`
- **Repositories**: Maven Central + Spring Snapshot repo (required for SNAPSHOT version)
- **Source layout**: Standard Maven/Gradle convention (`src/main/java`, `src/test/java`, `src/main/resources`)
