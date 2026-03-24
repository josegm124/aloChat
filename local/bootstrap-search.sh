#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="${STACK_NAME:-alo-dev-search}"
REGION="${AWS_REGION:-us-east-1}"

ENDPOINT="$(
  aws cloudformation describe-stacks \
    --region "${REGION}" \
    --stack-name "${STACK_NAME}" \
    --query 'Stacks[0].Outputs[?OutputKey==`SearchCollectionEndpoint`].OutputValue' \
    --output text
)"

if [[ -z "${ENDPOINT}" || "${ENDPOINT}" == "None" ]]; then
  echo "search collection endpoint not available for stack ${STACK_NAME}" >&2
  exit 1
fi

python3 tools/regulatory_search/bootstrap_search.py \
  --region "${REGION}" \
  --endpoint "${ENDPOINT}" \
  --embedding-model-id "${ALO_BEDROCK_EMBEDDING_MODEL_ID:-amazon.titan-embed-text-v2:0}" \
  --embedding-dimensions "${ALO_BEDROCK_EMBEDDING_DIMENSIONS:-1024}"
