#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/local/docker-compose.yml"
LOG_DIR="$ROOT_DIR/local/logs"
PID_DIR="$ROOT_DIR/local/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

docker compose -f "$COMPOSE_FILE" up -d

echo "Waiting for Kafka and DynamoDB Local..."
sleep 10

start_service() {
  local module="$1"
  local pid_file="$PID_DIR/${module}.pid"
  local log_file="$LOG_DIR/${module}.log"

  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "$module already running"
    return
  fi

  (
    cd "$ROOT_DIR"
    export ALO_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
    export ALO_DYNAMODB_ENDPOINT="http://localhost:8000"
    export AWS_REGION="us-east-1"
    export AWS_ACCESS_KEY_ID="local"
    export AWS_SECRET_ACCESS_KEY="local"
    ./gradlew ":services:${module}:bootRun"
  ) >"$log_file" 2>&1 &

  echo $! > "$pid_file"
  echo "Started $module (pid $(cat "$pid_file"))"
}

start_service "alo-profile-service"
start_service "alo-document-compliance-service"
start_service "alo-dataset-compliance-service"
start_service "alo-assessment-orchestrator"
start_service "alo-report-service"
start_service "alo-notification-service"
start_service "alo-retry-router-service"
start_service "alo-intake-service"

echo "Local stack started."
echo "Logs: $LOG_DIR"
