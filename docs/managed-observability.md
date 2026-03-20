# Managed Observability

Este bloque completa la observabilidad operativa con:

- `Amazon Managed Service for Prometheus`
- `Amazon Managed Grafana`
- un collector ECS basado en `aws-otel-collector`
- alarmas por email para:
  - latencia de API Gateway
  - fallos de Telegram
  - fallos de OpenSearch
  - fallos de Redis
  - eventos DLQ

## Prerrequisitos

- `foundation.yaml` actualizado y desplegado para crear el repo ECR `observability-collector`
- `runtime.yaml` actualizado y desplegado para habilitar Cloud Map y trafico interno de metricas
- `IAM Identity Center` habilitado para poder crear el workspace de Amazon Managed Grafana con `AWS_SSO`

## Archivos involucrados

- `infra/managed-observability.yaml`
- `infra/parameters/dev/managed-observability.json`
- `tools/observability/collector/Dockerfile`
- `tools/observability/collector/collector-config.yaml`

## 1. Construir y subir la imagen del collector

Primero identifica el repo ECR nuevo desde `foundation`:

```bash
aws cloudformation describe-stacks \
  --region us-east-1 \
  --stack-name alochat-dev-foundation \
  --query 'Stacks[0].Outputs[?OutputKey==`ObservabilityCollectorRepositoryUri`].OutputValue' \
  --output text
```

Luego construye y sube la imagen:

```bash
aws ecr get-login-password --region us-east-1 | \
docker login --username AWS --password-stdin 668778694151.dkr.ecr.us-east-1.amazonaws.com

docker build \
  -t 668778694151.dkr.ecr.us-east-1.amazonaws.com/alochat/dev/observability-collector:latest \
  -f tools/observability/collector/Dockerfile \
  tools/observability/collector

docker push 668778694151.dkr.ecr.us-east-1.amazonaws.com/alochat/dev/observability-collector:latest
```

## 2. Sacar `HttpApiId` del runtime

El stack managed necesita el `HttpApiId`, no solo la URL:

```bash
aws cloudformation describe-stacks \
  --region us-east-1 \
  --stack-name alochat-dev-runtime \
  --query 'Stacks[0].Outputs[?OutputKey==`HttpApiId`].OutputValue' \
  --output text
```

Actualiza ese valor en:

- `infra/parameters/dev/managed-observability.json`

## 3. Desplegar el stack managed

```bash
PARAMS=$(python3 - <<'PY'
import json
with open('infra/parameters/dev/managed-observability.json', encoding='utf-8') as stream:
    data = json.load(stream)
print(' '.join(f"{item['ParameterKey']}={item['ParameterValue']}" for item in data))
PY
)

aws cloudformation deploy \
  --region us-east-1 \
  --stack-name alochat-dev-managed-observability \
  --template-file infra/managed-observability.yaml \
  --parameter-overrides $PARAMS \
  --capabilities CAPABILITY_NAMED_IAM
```

## 4. Confirmar suscripcion del email

Amazon SNS enviara un correo a:

- `conjguerrero@gmail.com`

Debes abrirlo y confirmar la suscripcion.

## 5. Verificaciones utiles

Workspace AMP:

```bash
aws cloudformation describe-stacks \
  --region us-east-1 \
  --stack-name alochat-dev-managed-observability \
  --query 'Stacks[0].Outputs[?OutputKey==`AmpRemoteWriteUrl`].OutputValue' \
  --output text
```

Workspace Grafana:

```bash
aws cloudformation describe-stacks \
  --region us-east-1 \
  --stack-name alochat-dev-managed-observability \
  --query 'Stacks[0].Outputs[?OutputKey==`GrafanaWorkspaceEndpoint`].OutputValue' \
  --output text
```

Collector ECS:

```bash
aws ecs describe-services \
  --region us-east-1 \
  --cluster alochat-dev-cluster \
  --services alochat-dev-observability-collector
```

## Qué queda cubierto

- metricas de Spring Boot `/actuator/prometheus` hacia AMP
- dashboards en AMG usando AMP y CloudWatch
- alertas por email sobre fallos operativos relevantes

## Qué no queda cubierto aun

- dashboards Grafana preprovisionados
- reglas PromQL avanzadas dentro de AMP
- trazas distribuidas

Eso puede ir en la siguiente iteracion sin cambiar la base de despliegue.
