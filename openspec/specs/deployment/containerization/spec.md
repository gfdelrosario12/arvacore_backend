# deployment/containerization Specification

## Purpose
Provides containerized runtime environment definitions, environment variable bindings, graceful shutdown lifecycle management, automated HTTPS reverse proxy orchestration, and multi-container orchestration.

## Requirements

### Requirement: System SHALL provide a production multi-stage Docker container build
The system SHALL provide a multi-stage `Dockerfile` compiling the Spring Boot application using Java 17, running under a dedicated non-root user, and leveraging layer caching for dependencies.

#### Scenario: Production Docker build and execution
- **WHEN** the container image is built and started in production mode
- **THEN** application runs under a non-root user with optimized JVM memory flags and unprivileged file access

### Requirement: System SHALL support environment-based configuration without embedded credentials
The system SHALL externalize all infrastructure endpoints, credentials, JWT secrets, and database connection settings via environment variables with standard fallback defaults for dev/local execution.

#### Scenario: Custom environment configuration override
- **WHEN** environment variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `MQTT_BROKER_URL` are set
- **THEN** backend connects to the specified external infrastructure endpoints without using default fallbacks

### Requirement: System SHALL enforce graceful shutdown on SIGTERM
The system SHALL handle `SIGTERM` signals by stopping acceptance of new requests, completing active in-flight requests, and cleanly shutting down database and MQTT connections within the configured grace period.

#### Scenario: Graceful container termination
- **WHEN** container receives a `SIGTERM` signal
- **THEN** HTTP server finishes processing active requests before process termination

### Requirement: System SHALL provide multi-container orchestration for local development and production
The system SHALL provide `docker-compose.yml` for local development and `docker-compose.prod.yml` for production on the HostHatch VPS, orchestrating PostgreSQL 15, Eclipse Mosquitto MQTT broker, Caddy reverse proxy, and the Spring Boot application container with health-check dependency ordering on isolated network boundaries.

#### Scenario: Local stack startup via Docker Compose
- **WHEN** `docker compose up` is executed
- **THEN** PostgreSQL, Mosquitto, and the Spring Boot backend container start in proper dependency order and achieve healthy status

#### Scenario: Production stack startup with isolated network
- **WHEN** `docker compose -f docker-compose.prod.yml up -d` is executed on the production server
- **THEN** PostgreSQL, Mosquitto, backend, and Caddy start on the isolated `aquaflow-net` bridge network without conflicting with existing server containers on ports 8080 or 8081

### Requirement: System SHALL provide production reverse proxy with automated TLS
The system SHALL provide `Caddyfile` orchestrating the AquaFlow backend behind Caddy on ports 80/443 with automated Let's Encrypt TLS for `api-aquaflow.gladwindr.me`.

#### Scenario: HTTPS reverse proxy routing
- **WHEN** incoming HTTPS traffic arrives at `api-aquaflow.gladwindr.me`
- **THEN** Caddy terminates TLS and proxies requests to `backend:8080`, injecting standard forwarded headers

### Requirement: System SHALL support container secret files
The system SHALL support Docker secrets mounted at `/run/secrets/` via an entrypoint script before application boot.

#### Scenario: Container secrets resolution
- **WHEN** Docker mounts secret files for `db_password`, `jwt_secret`, or `webhook_secret` at `/run/secrets/`
- **THEN** `docker-entrypoint.sh` exports them into standard environment variables before launching the JVM

### Requirement: System SHALL support reverse proxy header forwarding
The system SHALL evaluate forwarded headers (`X-Forwarded-Proto`, `X-Forwarded-For`, `X-Forwarded-Host`) using `server.forward-headers-strategy=framework` to preserve HTTPS scheme and client host in API documentation and redirects.

#### Scenario: Request through HTTPS reverse proxy
- **WHEN** a client queries an endpoint or OpenAPI spec through Caddy with `X-Forwarded-Proto: https`
- **THEN** Spring Boot treats the request as secure and generates `https://` URLs in redirects and OpenAPI specifications

### Requirement: System SHALL support unified Flyway database migrations
The system SHALL execute unified ANSI SQL migration scripts supporting PostgreSQL in production and H2 in local/test environments without version conflicts or reserved keyword parsing failures.

#### Scenario: Flyway database migration on PostgreSQL
- **WHEN** Spring Boot connects to PostgreSQL in production mode
- **THEN** Flyway executes `V1__init_schema.sql` and `V2__edge_irrigation_schema.sql` sequentially and marks the schema history as valid
