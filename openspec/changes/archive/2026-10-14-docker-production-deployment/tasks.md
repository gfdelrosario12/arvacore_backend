# Tasks: Docker Production Deployment

## Status: Complete

- [x] Inspect backend source, dependencies, build process, configuration, API routes, and existing deployment files
- [x] Implement multi-stage production `Dockerfile` with non-root security and layer caching
- [x] Create comprehensive `.dockerignore` to reduce build context and exclude secrets
- [x] Create `docker-entrypoint.sh` to load Docker secret files from `/run/secrets/` into Spring Boot environment variables
- [x] Implement `docker-compose.prod.yml` with isolated network (`aquaflow-net`), port mapping (`8082` to avoid GLPI/Vaultwarden conflicts), and localhost database binding
- [x] Implement `Caddyfile` for automated Let's Encrypt TLS termination for `api-aquaflow.gladwindr.me`
- [x] Update `application.properties` and `application-prod.properties` with reverse proxy forward headers strategy (`server.forward-headers-strategy=framework`), graceful shutdown (`server.shutdown=graceful`), and production logging
- [x] Eliminate hardcoded MQTT broker URL in `MqttConfig.java` to support Docker service name resolution
- [x] Fix Flyway database migration blocker: resolved duplicate version 1 conflicts, quoted SQL reserved keyword `"value"`, and verified clean migrations via `FlywayMigrationTest`
- [x] Trim CORS allowed origins in `SecurityConfig.java` to prevent header parsing errors
- [x] Provide accurate `.env.example` and `.env.production` templates
- [x] Run full test suite (188 tests passing) and validate Docker Compose configuration
- [x] Sync delta specifications to main specs in `openspec/specs/`
- [x] Archive change to `openspec/changes/archive/2026-10-14-docker-production-deployment`
