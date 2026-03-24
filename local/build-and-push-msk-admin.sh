#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGION="${AWS_REGION:-us-east-1}"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
TAG="${1:-$(date +%Y%m%d-%H%M%S)}"

REPOSITORY_NAME="alo/dev/alo-msk-admin"
IMAGE_URI="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${REPOSITORY_NAME}:${TAG}"

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

aws ecr describe-repositories --region "$REGION" --repository-names "$REPOSITORY_NAME" >/dev/null 2>&1 \
  || aws ecr create-repository --region "$REGION" --repository-name "$REPOSITORY_NAME" >/dev/null

docker build \
  -f "$ROOT_DIR/docker/msk-admin.Dockerfile" \
  -t "$IMAGE_URI" \
  "$ROOT_DIR"

docker push "$IMAGE_URI"
echo "alo-msk-admin=${IMAGE_URI}"
