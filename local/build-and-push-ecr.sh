#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGION="${AWS_REGION:-us-east-1}"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
TAG="${1:-$(date +%Y%m%d-%H%M%S)}"

declare -a SERVICES=(
  "alo-intake-service"
  "alo-profile-service"
  "alo-document-compliance-service"
  "alo-dataset-compliance-service"
  "alo-assessment-orchestrator"
  "alo-report-service"
  "alo-notification-service"
  "alo-retry-router-service"
)

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

for service in "${SERVICES[@]}"; do
  echo "Building ${service}..."
  (cd "$ROOT_DIR" && ./gradlew ":services:${service}:bootJar")

  jar_path="$(find "$ROOT_DIR/services/${service}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' | head -n 1)"
  if [[ -z "${jar_path}" ]]; then
    echo "No jar built for ${service}" >&2
    exit 1
  fi

  image_uri="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/alo/dev/${service}:${TAG}"
  if ! aws ecr describe-repositories --region "$REGION" --repository-names "alo/dev/${service}" >/dev/null; then
    echo "ECR repository alo/dev/${service} is not accessible. Pre-create it or grant ecr:DescribeRepositories." >&2
    exit 1
  fi

  docker build \
    -f "$ROOT_DIR/docker/service.Dockerfile" \
    --build-arg "JAR_FILE=${jar_path#$ROOT_DIR/}" \
    -t "$image_uri" \
    "$ROOT_DIR"

  docker push "$image_uri"
  echo "${service}=${image_uri}"
done
