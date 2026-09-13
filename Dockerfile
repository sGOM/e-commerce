# syntax=docker/dockerfile:1

# ---- Build stage: Gradle 로 bootJar 생성 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시 최적화: 빌드 스크립트/래퍼 먼저 복사
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew
# 의존성 미리 내려받아 레이어 캐싱(소스 변경 시 재다운로드 방지)
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 부트 jar 빌드(테스트는 Testcontainers/Docker 필요 → 스킵)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage: JRE 만 포함한 경량 이미지 ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 비루트 사용자로 실행
RUN groupadd --system app && useradd --system --gid app app

# -plain.jar 을 제외한 실행 가능한 bootJar 만 복사
COPY --from=build /workspace/build/libs/starter-*.jar /app/app.jar
RUN test -f /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
