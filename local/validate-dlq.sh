#!/usr/bin/env bash
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
CLUSTER_NAME="${CLUSTER_NAME:-alo-dev-cluster}"
RUNTIME_STACK_NAME="${RUNTIME_STACK_NAME:-alo-dev-runtime}"
FOUNDATION_STACK_NAME="${FOUNDATION_STACK_NAME:-alochat-dev-foundation}"
ADMIN_TASK_DEFINITION="${ADMIN_TASK_DEFINITION:-alo-dev-alo-msk-admin}"
DATASET_LOG_GROUP="${DATASET_LOG_GROUP:-/aws/alo/dev/alo-dataset-compliance-service}"
ADMIN_LOG_GROUP="${ADMIN_LOG_GROUP:-/aws/alo/dev/alo-msk-admin}"
DATASET_GROUP_ID="${DATASET_GROUP_ID:-alo-dataset-compliance-service}"
TOPIC_NAME="${TOPIC_NAME:-alo.assessment.dataset.requested}"
DLQ_TOPIC_NAME="${DLQ_TOPIC_NAME:-alo.dlq}"

bootstrap_servers="${KAFKA_BOOTSTRAP_SERVERS:-}"
if [[ -z "$bootstrap_servers" ]]; then
  bootstrap_servers="$(python3 - <<'PY'
import json
with open("infra/parameters/dev/runtime.json", "r", encoding="utf-8") as fh:
    data = json.load(fh)
for item in data:
    if item["ParameterKey"] == "KafkaBootstrapServers":
        print(item["ParameterValue"])
        break
PY
)"
fi

private_subnets="$(aws cloudformation list-exports \
  --region "$REGION" \
  --query "Exports[?Name=='${FOUNDATION_STACK_NAME}-PrivateSubnetIds'].Value" \
  --output text)"
application_sg="$(aws cloudformation list-exports \
  --region "$REGION" \
  --query "Exports[?Name=='${FOUNDATION_STACK_NAME}-ApplicationSecurityGroupId'].Value" \
  --output text)"

if [[ -z "$private_subnets" || -z "$application_sg" ]]; then
  echo "missing network configuration for validation" >&2
  exit 1
fi

network_configuration="awsvpcConfiguration={subnets=[${private_subnets//,/\,}],securityGroups=[${application_sg}],assignPublicIp=DISABLED}"
start_ms="$(($(date +%s) * 1000))"

run_admin_task() {
  local command="$1"
  local task_arn
  local overrides
  overrides="$(COMMAND="$command" python3 - <<'PY'
import json
import os
print(json.dumps({
    "containerOverrides": [
        {
            "name": "alo-msk-admin",
            "command": [os.environ["COMMAND"]],
        }
    ]
}))
PY
)"
  task_arn="$(aws ecs run-task \
    --region "$REGION" \
    --cluster "$CLUSTER_NAME" \
    --launch-type FARGATE \
    --task-definition "$ADMIN_TASK_DEFINITION" \
    --count 1 \
    --network-configuration "$network_configuration" \
    --overrides "$overrides" \
    --query 'tasks[0].taskArn' \
    --output text)"

  aws ecs wait tasks-stopped \
    --region "$REGION" \
    --cluster "$CLUSTER_NAME" \
    --tasks "$task_arn"

  local task_id log_stream
  task_id="${task_arn##*/}"
  log_stream="ecs/alo-msk-admin/${task_id}"

  aws logs get-log-events \
    --region "$REGION" \
    --log-group-name "$ADMIN_LOG_GROUP" \
    --log-stream-name "$log_stream" \
    --query 'events[].message' \
    --output text
}

inject_output="$(run_admin_task "timeout 20s sh -c 'printf \"not-json\\n\" | kafka-console-producer --producer-property max.block.ms=5000 --producer-property request.timeout.ms=5000 --producer-property delivery.timeout.ms=10000 --bootstrap-server ${bootstrap_servers} --topic ${TOPIC_NAME}'; rc=\$?; echo rc=\$rc; if [ \$rc -eq 0 ]; then echo sent; fi; exit \$rc")"
if ! grep -q "sent" <<<"$inject_output"; then
  echo "failed to inject invalid message" >&2
  echo "$inject_output" >&2
  exit 1
fi

sleep 5

dataset_events="$(aws logs filter-log-events \
  --region "$REGION" \
  --log-group-name "$DATASET_LOG_GROUP" \
  --start-time "$start_ms" \
  --query 'events[].message' \
  --output text)"

grep -q "retrying kafka message topic=${TOPIC_NAME}" <<<"$dataset_events"
grep -q "routing message to dlq topic=${DLQ_TOPIC_NAME} originalTopic=${TOPIC_NAME}" <<<"$dataset_events"

offsets_output="$(run_admin_task "timeout 20s sh -c 'echo OFFSETS; kafka-run-class kafka.tools.GetOffsetShell --bootstrap-server ${bootstrap_servers} --topic ${TOPIC_NAME} --time -1; kafka-run-class kafka.tools.GetOffsetShell --bootstrap-server ${bootstrap_servers} --topic ${DLQ_TOPIC_NAME} --time -1; echo GROUP; kafka-consumer-groups --bootstrap-server ${bootstrap_servers} --group ${DATASET_GROUP_ID} --describe'; rc=\$?; echo rc=\$rc; exit \$rc")"

dataset_group_line="$(grep "${DATASET_GROUP_ID}[[:space:]]\+${TOPIC_NAME}" <<<"$offsets_output" | tail -n 1)"
if [[ -z "$dataset_group_line" ]]; then
  echo "missing consumer group line for ${TOPIC_NAME}" >&2
  echo "$offsets_output" >&2
  exit 1
fi

lag_value="$(awk '{print $(NF-3)}' <<<"$dataset_group_line")"
if [[ "$lag_value" != "0" ]]; then
  echo "expected zero lag, got ${lag_value}" >&2
  echo "$offsets_output" >&2
  exit 1
fi

grep -q "${DLQ_TOPIC_NAME}:0:" <<<"$offsets_output"

echo "poison message validation passed"
echo "$dataset_group_line"
