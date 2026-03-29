#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/local/docker-compose.yml"
LOG_DIR="$ROOT_DIR/local/logs"

mkdir -p "$LOG_DIR"

build_service_jar() {
  local module="$1"
  (
    cd "$ROOT_DIR"
    ./gradlew --no-daemon ":services:${module}:bootJar"
  )
}

build_service_jar "alo-intake-service"
build_service_jar "alo-document-compliance-service"
build_service_jar "alo-report-service"
build_service_jar "alo-notification-service"

docker compose -f "$COMPOSE_FILE" up -d --build

echo "Waiting for local services..."
sleep 10

echo "Local stack started."
echo "Inspect with: docker compose -f $COMPOSE_FILE ps"
echo "Logs with:   docker compose -f $COMPOSE_FILE logs -f"
