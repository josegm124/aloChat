#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-kafka:9092}"
TOPICS=(
  "messages.ingress.normalized"
  "messages.processing.ai"
  "messages.outbound.dispatch"
  "messages.retry.short"
  "messages.retry.long"
  "messages.dlq"
)

until /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --list >/dev/null 2>&1; do
  sleep 2
done

for topic in "${TOPICS[@]}"; do
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions 3 \
    --replication-factor 1
done
