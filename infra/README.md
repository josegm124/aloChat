# Infraestructura CloudFormation

Este directorio contiene la base de infraestructura modular para `aloChat`.

## Orden de despliegue
Con el hardening actual, el orden recomendado es:
1. `foundation.yaml`
2. `data.yaml`
3. `observability.yaml`
4. `msk.yaml`
5. `tenant-config.yaml`
6. `runtime.yaml` con `OpenSearchCollectionEndpoint` vacio o placeholder
7. `ai-rag.yaml` usando el output `AiTaskRoleArn` de `runtime`
8. actualizar `runtime.yaml` con el endpoint real de OpenSearch
9. `managed-observability.yaml` para AMP, AMG y collector Prometheus

## Objetivo de cada stack
- `foundation.yaml`: red, subnets, security groups y repositorios ECR.
- `data.yaml`: Redis transitorio, tablas DynamoDB para idempotencia/auditoria y bucket opcional de payloads crudos.
- `observability.yaml`: CloudWatch log groups, dashboard base y topic de alertas.
- `managed-observability.yaml`: Amazon Managed Service for Prometheus, Amazon Managed Grafana, collector ECS y alarmas enfocadas en latencia, Telegram, OpenSearch, Redis y DLQ.
- `runtime.yaml`: ECS Fargate, ALB y API Gateway para `inbound-adapter`, `processor`, `ai-orchestrator` y `outbound-dispatcher`.
  Tambien activa `messages.retry.short`, `messages.retry.long` y `messages.dlq` en el runtime.
- `ai-rag.yaml`: bucket S3 para corpus, coleccion OpenSearch Serverless y nombres de indices `kb_corpus` y `chat_memory`.
- `msk.yaml`: cluster Amazon MSK Serverless para Kafka.
- `tenant-config.yaml`: secretos iniciales por tenant para Web.

## Redis vs DynamoDB
- Redis en ElastiCache se usa para estado conversacional transitorio, contexto corto de la conversación, locks efímeros y enriquecimiento de proceso.
- DynamoDB se usa para idempotencia duradera, auditoría mínima, estado final del mensaje y reintentos seguros.

Si usamos solo Redis:
- perdemos trazabilidad duradera al expirar claves
- el reproceso es más riesgoso
- no hay un registro operativo confiable cuando se limpia la cache

## Despliegue manual sugerido
```bash
aws cloudformation create-stack \
  --stack-name alochat-dev-foundation \
  --template-body file://infra/foundation.yaml \
  --parameters file://infra/parameters/dev/foundation.json \
  --capabilities CAPABILITY_NAMED_IAM
```

Para `data.yaml`, primero necesitas los outputs del stack `foundation`.
Para `runtime.yaml`, necesitas outputs de `foundation`, `data`, `msk`, las URIs finales de imagen en ECR y la convencion de secretos Web por tenant.
En MSK Serverless, el bootstrap server se consulta despues con AWS CLI usando el ARN del cluster creado.
Para `ai-rag.yaml`, necesitas `VpcId`, `PrivateSubnetIds`, `EndpointSecurityGroupId` y el output `AiTaskRoleArn` del stack `runtime`.
Para `managed-observability.yaml`, necesitas el cluster ECS del runtime, subnets privadas, security group de aplicacion, `HttpApiId` y la imagen del collector Prometheus.

## CI/CD dev actual
El repo ya incluye automatizacion base con GitHub Actions:
- `.github/workflows/ci.yml`
- `.github/workflows/cd-dev.yml`

`ci.yml`:
- corre en `pull_request`
- corre en `push` a `master`
- ejecuta `./gradlew test` y `./gradlew build -x test`

`cd-dev.yml`:
- corre en `push` a `master`
- tambien se puede correr por `workflow_dispatch`
- construye/pushea imagenes a ECR via `jib`
- despliega `infra/runtime.yaml` con `aws cloudformation deploy`
- espera estabilidad de servicios ECS

## GitHub OIDC -> AWS
Configuracion operativa actual para `cd-dev.yml`:
- OIDC provider AWS: `token.actions.githubusercontent.com`
- IAM role: `github-actions-alochat-dev`
- GitHub Environment: `dev`
- variables GitHub:
  - `AWS_REGION`
  - `ECR_REGISTRY`
  - `AWS_ROLE_ARN`
- secret GitHub:
  - `TELEGRAM_BOT_TOKEN`

Detalle critico:
- `cd-dev.yml` usa `environment: dev`
- por eso el `sub` del token OIDC debe ser:
  - `repo:josegm124/aloChat:environment:dev`
- si el trust policy del role se configura con:
  - `repo:josegm124/aloChat:ref:refs/heads/master`
- el paso `aws-actions/configure-aws-credentials@v4` falla con:
  - `Not authorized to perform sts:AssumeRoleWithWebIdentity`

Activacion:
- `CI`: al abrir/actualizar PR o hacer `push` a `master`
- `CD Dev`: al hacer `push` a `master` o manualmente desde `Actions`

## Hardening actual
`foundation.yaml` ahora deja:
- NAT Gateway opcional
- VPC endpoints para Secrets Manager, CloudWatch Logs, ECR, STS y S3

`runtime.yaml` ahora deja:
- servicios ECS en subnets privadas
- sin IP publica en tareas
- WAF regional sobre el ALB
- throttling en API Gateway
- rutas explicitas en lugar de proxy amplio
- service discovery interno por Cloud Map para `inbound-adapter`, `processor`, `ai-orchestrator` y `outbound-dispatcher`
- trafico interno entre tareas habilitado en puertos de metricas `8080-8083`

`ai-rag.yaml` ahora deja:
- OpenSearch Serverless sin acceso publico
- acceso de datos solo para el rol IAM del `ai-orchestrator`
- acceso por VPC endpoint privado

`managed-observability.yaml` ahora deja:
- workspace AMP con endpoint Prometheus
- workspace AMG para dashboards
- collector ECS que scrapea `/actuator/prometheus` desde Cloud Map y hace remote write a AMP
- alertas por email para latencia, Telegram, OpenSearch, Redis y eventos DLQ

## Notas operativas
- Amazon Managed Grafana en este template usa `AWS_SSO`; necesitas IAM Identity Center habilitado en la cuenta/región antes del despliegue.
- El collector usa una imagen propia basada en `aws-otel-collector`; el `Dockerfile` y la config viven en `tools/observability/collector/`.
