## ADDED Requirements

### Requirement: System SHALL provide production orchestration with reverse proxy and automated TLS
The system SHALL provide `docker-compose.prod.yml` and `Caddyfile` orchestrating the AquaFlow backend behind Caddy on ports 80/443 with automated Let's Encrypt TLS for `api-aquaflow.gladwindr.me`.

#### Scenario: Production stack startup with isolated network
- **WHEN** `docker compose -f docker-compose.prod.yml up -d` is executed on the production server
- **THEN** PostgreSQL, Mosquitto, backend, and Caddy start on the isolated `aquaflow-net` bridge network without conflicting with existing server containers on ports 8080 or 8081

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

## MODIFIED Requirements

### Requirement: System SHALL support unified Flyway database migrations
The system SHALL execute unified ANSI SQL migration scripts supporting PostgreSQL in production and H2 in local/test environments without version conflicts or reserved keyword parsing failures.

#### Scenario: Flyway database migration on PostgreSQL
- **WHEN** Spring Boot connects to PostgreSQL in production mode
- **THEN** Flyway executes `V1__init_schema.sql` and `V2__edge_irrigation_schema.sql` sequentially and marks the schema history as valid
