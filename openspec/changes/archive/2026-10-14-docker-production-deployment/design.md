# Docker Production Deployment — Design

## Architecture

```
Internet
  │
  ▼ (DNS: api-aquaflow.gladwindr.me → 167.104.100.133)
┌─────────────────────────────────────────────────────────┐
│  agama-core VPS                                         │
│                                                         │
│  Existing (untouched):                                  │
│    ├── GLPI           → :8080 (0.0.0.0)                 │
│    ├── Vaultwarden    → 127.0.0.1:8081                  │
│    ├── Hani Bot                                         │
│    └── MariaDB (GLPI) → :3306                           │
│                                                         │
│  New (aquaflow-net):                                    │
│    ├── Caddy          → :80, :443 (auto-HTTPS)          │
│    ├── Backend        → 127.0.0.1:8082 (internal)       │
│    ├── PostgreSQL 15  → 127.0.0.1:5432 (internal)       │
│    └── Mosquitto      → :1883, :9001                    │
└─────────────────────────────────────────────────────────┘
```

## Key Decisions

1. **Multi-stage Dockerfile**: Build stage with JDK + Maven layer caching, runtime stage with minimal JRE Alpine and non-root user `aquaflow`.
2. **Caddy Reverse Proxy**: Terminates TLS and auto-provisions Let's Encrypt certificates for `api-aquaflow.gladwindr.me`. Proxies traffic to `backend:8080`.
3. **Port Isolation (8082)**: Backend binds to `127.0.0.1:8082` to avoid collision with GLPI (8080) and Vaultwarden (8081). PostgreSQL binds to `127.0.0.1:5432` to prevent public database exposure.
4. **Isolated Bridge Network**: `aquaflow-net` isolates all AquaFlow containers from existing server containers.
5. **Docker Secrets Resolution**: `docker-entrypoint.sh` loads `/run/secrets/*` (`db_password`, `jwt_secret`, `webhook_secret`) into standard Spring Boot environment variables before JVM execution.
6. **Reverse Proxy Header Forwarding**: Enabled `server.forward-headers-strategy=framework` so Spring Boot recognizes `X-Forwarded-Proto: https` from Caddy, correctly generating HTTPS URLs in redirects and OpenAPI specifications.
7. **Flyway Migration Resolution**: Removed duplicate `V1__init_schema_postgresql.sql` which triggered startup crashes; quoted reserved keywords `"value"` and `"timestamp"` in `V1__init_schema.sql`; verified clean migrations via `FlywayMigrationTest`.
8. **Graceful Shutdown**: Added `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s` to finish active requests on SIGTERM.
9. **CORS & MQTT Resilience**: Injected `MQTT_BROKER_URL` dynamically via `@Value`; trimmed origins in `SecurityConfig.java`.

## Files Changed

| File | Action | Purpose |
| :--- | :--- | :--- |
| `Dockerfile` | Replaced | Multi-stage build, non-root, JVM tuning, health check |
| `.dockerignore` | Replaced | Exclude AI tools, secrets, IDE configs from build context |
| `docker-entrypoint.sh` | Created | Container secret loader for `/run/secrets/` |
| `docker-compose.prod.yml` | Created | Production compose with Caddy, PostgreSQL, Mosquitto, backend |
| `Caddyfile` | Created | Automated Let's Encrypt HTTPS reverse proxy |
| `.env.example` | Replaced | Comprehensive environment variable template |
| `.env.production` | Replaced | Production env var template for `agama-core` VPS |
| `mosquitto/config/mosquitto.conf` | Replaced | Functional Mosquitto MQTT & WebSocket configuration |
| `MqttConfig.java` | Modified | Broker URL driven by `MQTT_BROKER_URL` env var |
| `SecurityConfig.java` | Modified | Trim CORS origins to prevent header parse failures |
| `application.properties` | Modified | Forwarded headers strategy, graceful shutdown, unified secret properties |
| `application-prod.properties` | Modified | Production port, datasource URL, actuator probes |
| `V1__init_schema.sql` | Modified | Quoted `"value"`, unified ANSI SQL migration |
| `V1__init_schema_postgresql.sql` | Deleted | Removed duplicate V1 migration to eliminate Flyway conflict |
| `FlywayMigrationTest.java` | Created | Regression test verifying Flyway migrations apply cleanly |
