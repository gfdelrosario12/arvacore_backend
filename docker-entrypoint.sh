#!/bin/sh
set -e

# Load Docker secret files if present and environment variables are not already set
if [ -f /run/secrets/db_password ] && [ -z "$SPRING_DATASOURCE_PASSWORD" ]; then
    export SPRING_DATASOURCE_PASSWORD="$(tr -d '\r\n' < /run/secrets/db_password)"
fi

if [ -f /run/secrets/jwt_secret ] && [ -z "$JWT_SECRET" ]; then
    export JWT_SECRET="$(tr -d '\r\n ' < /run/secrets/jwt_secret)"
fi

if [ -f /run/secrets/webhook_secret ] && [ -z "$LORAWAN_WEBHOOK_SECRET" ]; then
    export LORAWAN_WEBHOOK_SECRET="$(tr -d '\r\n ' < /run/secrets/webhook_secret)"
fi

exec "$@"

