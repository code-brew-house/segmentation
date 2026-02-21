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
	implementation("org.springframework.boot:spring-boot-starter")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
