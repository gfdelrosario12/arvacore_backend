# Docker Production Deployment — Proposal

## Summary

Create production-ready Docker deployment files for the AquaFlow Spring Boot backend,
targeting the HostHatch VPS (agama-core, 167.104.100.133) that already runs GLPI (8080),
Vaultwarden (8081), and Hani Bot containers.

## Motivation

The backend currently has a skeleton Dockerfile (no multi-stage build, no security hardening)
and a docker-compose.yml designed for local development. We need production-grade deployment
that:

- Builds efficiently with Maven dependency caching
- Runs as non-root with JVM container tuning
- Avoids port conflicts with existing services
- Provides automatic HTTPS via Caddy reverse proxy
- Uses file-based Docker secrets instead of hardcoded credentials
- Documents the complete deployment workflow

## Non-goals

- CI/CD pipeline (GitHub Actions) — deferred
- Kubernetes / container orchestration
- Database backups automation
- MQTT authentication/TLS (the current Java config uses anonymous access)
- Modifying application business logic

## Impact

- No disruption to existing GLPI, Vaultwarden, Hani Bot containers
- Uses dedicated `aquaflow-net` bridge network (isolated)
- Backend listens on `127.0.0.1:8082` (internal), exposed via Caddy on 80/443
- Caddy binds ports 80 and 443 (must be free on the server)

