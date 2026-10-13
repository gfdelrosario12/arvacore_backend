#!/bin/sh
set -e

# Load Docker secret files if present and environment variables are not already set
if [ -f /run/secrets/db_password ] && [ -z "$SPRING_DATASOURCE_PASSWORD" ]; then
    export SPRING_DATASOURCE_PASSWORD="$(cat /run/secrets/db_password)"
fi

if [ -f /run/secrets/jwt_secret ] && [ -z "$JWT_SECRET" ]; then
    export JWT_SECRET="$(cat /run/secrets/jwt_secret)"
fi

if [ -f /run/secrets/webhook_secret ] && [ -z "$LORAWAN_WEBHOOK_SECRET" ]; then
    export LORAWAN_WEBHOOK_SECRET="$(cat /run/secrets/webhook_secret)"
fi

exec "$@"

