plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Docker Engine 29 호환: Boot 3.5.3 BOM 의 Testcontainers 1.21.2 는 Docker 29 에 접속하지 못한다
// ("Could not find a valid Docker environment"). 1.21.4 에서 해결돼 버전만 올린다.
extra["testcontainers.version"] = "1.21.4"

val kotlinJdslVersion = "3.5.4"
val springMockkVersion = "4.0.2"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 업로드 이미지 S3 저장(upload.storage=s3 일 때만 사용). 로컬 기본은 디스크 저장
    implementation("software.amazon.awssdk:s3:2.46.7")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlin JDSL (동적/복잡 쿼리)
    implementation("com.linecorp.kotlin-jdsl:jpql-dsl:$kotlinJdslVersion")
    implementation("com.linecorp.kotlin-jdsl:jpql-render:$kotlinJdslVersion")
    implementation("com.linecorp.kotlin-jdsl:spring-data-jpa-support:$kotlinJdslVersion")

    // DB / Migration
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // API 문서 (OpenAPI 3 / Swagger UI) — 2.8.x 가 Boot 3.5 대응(3.x 는 Boot 4)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.ninja-squad:springmockk:$springMockkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Testcontainers 우회용 외부 DB 지정(-D)을 포크된 테스트 JVM 으로 전달한다.
    listOf("it.datasource.url", "it.datasource.username", "it.datasource.password").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}

// 포맷 검사: 규칙은 루트 .editorconfig. CLI 로 측정한 버전에 고정해 로컬·CI 위반 집합을 같게 둔다.
ktlint {
    version.set("1.5.0")
}
