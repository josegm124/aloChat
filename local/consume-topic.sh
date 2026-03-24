#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/local/docker-compose.yml"
TOPIC="${1:?usage: consume-topic.sh <topic> [count]}"
COUNT="${2:-1}"

docker compose -f "$COMPOSE_FILE" exec redpanda \
  rpk topic consume "$TOPIC" --brokers redpanda:9092 -n "$COUNT"
