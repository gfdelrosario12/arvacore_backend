# =============================================================================
# AquaFlow Backend — Production Dockerfile
# Multi-stage build: Maven build → JRE runtime
# =============================================================================

# --------------- Stage 1: Build ---------------
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Cache Maven wrapper and dependencies first
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B

# Copy sources and build (skip tests — they run in CI)
COPY src/ src/
RUN ./mvnw package -DskipTests -B && \
    mv target/*.jar target/app.jar

# --------------- Stage 2: Runtime ---------------
FROM eclipse-temurin:17-jre-alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

# Security: run as non-root
RUN addgroup -S aquaflow && adduser -S aquaflow -G aquaflow

WORKDIR /app

COPY --from=build /app/target/app.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh && chown aquaflow:aquaflow /app/docker-entrypoint.sh

# Spring Boot Actuator health endpoint for Docker healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Default Spring Boot port
EXPOSE 8080

USER aquaflow

ENTRYPOINT ["/app/docker-entrypoint.sh", "java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]