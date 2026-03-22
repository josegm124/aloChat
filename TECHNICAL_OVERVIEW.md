# aloChat - Resumen Tecnico Integral de la Carpeta

Este documento consolida, en una sola vista tecnica y teorica, lo que cubre el repo `aloChat/` a nivel de arquitectura, modulos, flujo de datos, infraestructura, seguridad y operacion.

## 1. Alcance del sistema

`aloChat` implementa una plataforma omnicanal orientada a mensajeria conversacional con pipeline asincrono:

1. ingreso por canal (`web`, `telegram`, `meta`)
2. normalizacion a contrato canonico
3. procesamiento e idempotencia
4. generacion/respuesta AI con memoria y conocimiento
5. despacho por canal
6. auditoria, observabilidad y reintentos

Modelo tecnologico base:
- Java 21 + Spring Boot 3.x
- Kafka (local en Docker / AWS MSK Serverless en cloud)
- Redis para estado transitorio
- DynamoDB para durabilidad operativa (idempotencia/auditoria/memoria/hints)
- OpenSearch Serverless para `kb_corpus` y `chat_memory` (cuando endpoint esta configurado)
- CloudFormation modular para infraestructura

## 2. Estructura del repositorio (vision funcional)

- `libs/message-contracts/`: contrato canonico compartido entre servicios.
- `services/inbound-adapter/`: API de entrada, autenticacion web HMAC, adaptadores por canal, publicacion inicial a Kafka.
- `services/processor/`: idempotencia, estado conversacional de corto plazo, auditoria y enrutamiento a AI.
- `services/ai-orchestrator/`: retrieval, memoria conversacional, generacion de respuesta, hints de campaña, envio a outbound.
- `services/outbound-dispatcher/`: despacho por canal, cierre de estado y limpieza de estado transitorio.
- `infra/`: stacks CloudFormation por dominio (foundation, data, runtime, MSK, RAG, observability, tenant config).
- `infra/parameters/dev/`: parametros listos para despliegues en `dev`.
- `local/`: ambiente local con Docker Compose (Kafka, Redis, Prometheus, Grafana).
- `tools/knowledge/`: scripts Python para ingesta y verificacion del corpus en OpenSearch.
- `tools/observability/collector/`: config e imagen del collector OTEL para AMP.
- `tools/msk-admin/`: utilitario Java para bootstrap de topicos en MSK.
- `docs/`: documentacion tecnica por tema (contratos, RAG, tenancy, secretos, observabilidad, etc.).
- `fixtures/channels/`: payloads de ejemplo por canal.
- `aws/`: bundle de AWS CLI v2 y artefactos auxiliares de despliegue.

## 3. Build, modulos y empaquetado

### 3.1 Multi-modulo Gradle
`settings.gradle` incluye:
- `libs:message-contracts`
- `tools:msk-admin`
- `services:inbound-adapter`
- `services:processor`
- `services:ai-orchestrator`
- `services:outbound-dispatcher`

### 3.2 Stack de servicios
Cada microservicio usa:
- Spring Boot `3.5.11`
- Java toolchain 21
- Jib para imagen contenedorizada

Puertos de contenedor (Jib):
- inbound-adapter: `8080`
- processor: `8081`
- outbound-dispatcher: `8082`
- ai-orchestrator: `8083`

## 4. Contrato canonico interno

`libs/message-contracts` define `MessageEnvelope` como record central:
- identificacion: `messageId`, `idempotencyKey`, `traceId`
- ruteo y contexto: `channel`, `tenantId`, `externalMessageId`, `conversationId`, `userId`, `receivedAt`
- contenido: `content` (`NormalizedContent`)
- enriquecimiento: `attachments`, `metadata`, `rawPayloadRef`
- ciclo de vida: `status` (`MessageStatus`)

Enums principales:
- `Channel`: `WEB`, `TELEGRAM`, `META`
- `ContentType`: `TEXT`, `IMAGE`, `AUDIO`, `DOCUMENT`, `UNKNOWN`
- `MessageStatus`: `RECEIVED`, `NORMALIZED`, `ACCEPTED`, `PUBLISHED`, `PROCESSING`, `AI_PROCESSING`, `AI_COMPLETED`, `READY_FOR_DISPATCH`, `DISPATCHED`, `FAILED`

Headers de retry estandarizados:
- `x-target-service`
- `x-source-topic`
- `x-retry-stage`
- `x-retry-count`
- `x-failure-reason`

## 5. Topologia de topics Kafka

Topics canonicos del flujo:
- `messages.ingress.normalized`
- `messages.processing.ai`
- `messages.outbound.dispatch`
- `messages.retry.short`
- `messages.retry.long`
- `messages.dlq`

Se crean en local por `local/kafka/init-topics.sh`.
Tambien hay utilitario para MSK IAM: `tools/msk-admin`.

## 6. Flujo end-to-end

### 6.1 Inbound
Servicio: `services/inbound-adapter`

Endpoints:
- `POST /api/v1/inbound/web`
- `POST /api/v1/inbound/telegram`
- `POST /api/v1/inbound/meta`
- `GET /api/v1/web/messages/{messageId}`
- `GET /api/v1/web/messages/conversation/{conversationId}?limit=...`

Responsabilidades:
- validar payload por canal
- autenticar canal web por HMAC
- adaptar payload externo a `MessageEnvelope`
- generar `messageId`, `traceId` e `idempotencyKey`
- publicar a `messages.ingress.normalized`
- persistir auditoria inicial en DynamoDB (`message-audit`)

Autenticacion web HMAC:
- headers obligatorios: `x-tenant-id`, `x-timestamp`, `x-signature`
- firma esperada: `HMAC_SHA256(secret, x-timestamp + "." + rawBody)`
- ventana de tiempo validada por `alochat.web.max-clock-skew-seconds`
- secreto por tenant resuelto en Secrets Manager con convención:
  - `alochat/{env}/tenants/{tenantId}/channels/web/ingress-signing-key`

### 6.2 Processor
Servicio: `services/processor`

Consume:
- primario: `messages.ingress.normalized`
- retries: `messages.retry.short` y `messages.retry.long` (filtrando por `x-target-service=processor`)

Responsabilidades:
- registrar idempotencia durable (DynamoDB `idempotency`)
- si duplicado: no reprocesar
- mover a estado `PROCESSING`
- guardar estado conversacional transitorio en Redis (TTL configurable)
- auditar en DynamoDB
- publicar a AI topic (`messages.processing.ai`) con estado `AI_PROCESSING`

Estrategia de fallos:
- error en flujo primario -> retry short
- error en retry short -> retry long
- error en retry long -> DLQ

### 6.3 AI Orchestrator
Servicio: `services/ai-orchestrator`

Consume:
- primario: `messages.processing.ai`
- retries: `messages.retry.short` y `messages.retry.long` (filtrando `x-target-service=ai-orchestrator`)

Responsabilidades:
- construir contexto AI (`AiContextService`):
  - memoria conversacional por `memoryKey`
  - snippets de conocimiento con limite configurable
- generar respuesta (`KnowledgeAwareAiResponseGenerator`)
- persistir memoria conversacional y `campaign hints`
- auditar estados
- publicar a outbound con estado `READY_FOR_DISPATCH`

Modelo de retrieval:
- si hay `alochat.ai.opensearch.endpoint`: usa `OpenSearchKnowledgeRetriever`
- si no: fallback a `ClasspathStoreKnowledgeRetriever` sobre `store_inventory_2026.psv`

Caracteristicas de respuesta actual:
- heuristicas bilingues (`es`/`en`) segun metadata/locale
- ranking por intención (interior, exterior, techo, metal, madera, promociones, etc.)
- soporte de ofertas activas (`docType=offer`, `offerActive=true`)
- metadata de salida (`aiMode`, `aiProvider`, `memoryKey`, `knowledgeMatches`, etc.)

Memoria y campañas:
- key durable: `tenantId:channel:userId:conversationId`
- resumen incremental y tags de interés
- hint de recompra/repintado a largo plazo o vigilancia de descuento
- scheduler interno (`@EnableScheduling`) para materializar campañas

### 6.4 Outbound Dispatcher
Servicio: `services/outbound-dispatcher`

Consume:
- primario: `messages.outbound.dispatch`
- retries: `messages.retry.short` y `messages.retry.long` (filtrando `x-target-service=outbound-dispatcher`)

Responsabilidades:
- despachar por canal via `ChannelDispatcher`
- marcar `DISPATCHED`
- persistir auditoria final en DynamoDB
- limpiar estado conversacional transitorio en Redis

Estado por canal:
- Telegram: envio real via API Telegram (`/sendMessage`) con Resilience4j (retry + circuit breaker)
- Web: dispatcher placeholder (actualmente log/estructura)
- Meta: dispatcher placeholder (actualmente log/estructura)

## 7. Persistencia y modelo de datos

### 7.1 DynamoDB
Tabla/uso principal:
- `idempotency`: deduplicacion durable
- `message-audit`: trazabilidad de estados y consulta por `messageId`/`conversationId`
- `conversation-memory`: resumen durable por memoria
- `campaign-hints`: oportunidades y seguimientos proactivos

### 7.2 Redis
Uso:
- estado conversacional transitorio
- datos de proceso de corta vida
- limpieza al final del flujo o por TTL

### 7.3 OpenSearch Serverless
Indices logicos:
- `kb_corpus`: conocimiento de productos/politicas/promociones
- `chat_memory`: memoria indexada

Nota: el runtime habilita operación privada (VPC endpoint) en infra.

## 8. Configuracion runtime por servicio

Comunes:
- `spring.threads.virtual.enabled: true`
- Kafka configurable para `PLAINTEXT` local o `SASL/IAM` en AWS
- `management` expone `health,info,prometheus`

Configs destacadas:
- inbound-adapter:
  - `alochat.topics.normalized`
  - `alochat.web.secret-prefix/suffix`
  - `alochat.web.max-clock-skew-seconds`
- processor:
  - `alochat.topics.*` (normalized/ai/outbound/retry/dlq)
  - `alochat.redis.conversation-ttl`
  - `alochat.kafka.retry-delay.short-ms/long-ms`
- ai-orchestrator:
  - `alochat.ai.knowledge.catalog-resource`
  - `alochat.ai.opensearch.endpoint`
  - `alochat.ai.opensearch.knowledge-index/memory-index`
  - `alochat.ai.bedrock.embedding-model-id`
  - `alochat.campaigns.*`
- outbound-dispatcher:
  - `alochat.telegram.bot-username`
  - `alochat.telegram.api-base-url`
  - `resilience4j.retry/circuitbreaker` para dispatch Telegram

## 9. Infraestructura CloudFormation

Stacks en `infra/`:
- `foundation.yaml`: VPC, subnets, SG, ECR, endpoints base
- `data.yaml`: DynamoDB + Redis + bucket de payload crudo
- `observability.yaml`: CloudWatch logs/metrics/alarms base
- `msk.yaml`: cluster MSK Serverless
- `tenant-config.yaml`: secreto HMAC por tenant
- `runtime.yaml`: ECS Fargate + ALB + API Gateway + Cloud Map + IAM de runtime
- `ai-rag.yaml`: S3 corpus + OpenSearch Serverless collection/index naming
- `managed-observability.yaml`: AMP + AMG + collector ECS + alertas focalizadas

Orden recomendado de despliegue documentado:
1. foundation
2. data
3. observability
4. msk
5. tenant-config
6. runtime (endpoint OpenSearch vacio/placeholder)
7. ai-rag
8. actualizar runtime con endpoint real OpenSearch
9. managed-observability

## 10. Operacion local

`local/compose.yaml` levanta:
- Kafka KRaft (`19092` expuesto)
- init de topics
- Redis (`6379`)
- Prometheus (`9090`)
- Grafana (`3000`)

Objetivo: validar pipeline funcional y metricas antes/despues de despliegue cloud.

## 11. Tooling operativo

### 11.1 Conocimiento (`tools/knowledge`)
- `ingest_storage.py`:
  - parsea inventario
  - genera documentos producto/oferta
  - localiza por idioma
  - sube/indexa en OpenSearch con firma SigV4
- `verify_count.py`: verifica conteo de docs por indice
- `create_chat_memory_index.py`: crea mapping base para `chat_memory`

### 11.2 Observabilidad (`tools/observability/collector`)
- collector basado en `aws-otel-collector`
- scrapea `/actuator/prometheus` de los 4 servicios
- hace remote_write a AMP con `sigv4auth`

### 11.3 MSK admin (`tools/msk-admin`)
- utilitario Java para crear topicos canonicos en MSK con IAM SASL

## 12. Documentacion existente (por tema)

`docs/` contiene guias segmentadas:
- contratos inbound
- arquitectura RAG/memoria
- pipeline de conocimiento
- secretos
- tenancy
- observabilidad managed
- dashboard inicial Grafana

`README.md` y `infra/README.md` cubren arquitectura/deployment, pero no estaban concentrados en un unico documento integral de carpeta.

## 13. Estado tecnico actual y limites

Implementado y operativo en diseño:
- pipeline completo por eventos con retries y DLQ
- web + telegram + meta en entrada
- AI con retrieval y memoria
- auditoria durable y estado transitorio separado
- observabilidad por Prometheus/Grafana + CloudWatch/AMP/AMG

Limites actuales visibles en codigo:
- dispatch Web y Meta en outbound aun como placeholder (no cliente final de entrega)
- generacion AI principal aun heuristica/conservadora (Bedrock embeddings configurado para evolucion)
- dependencia de parametrizacion/secretos para entorno cloud productivo

## 14. Convenciones y decisiones de diseño

Principios dominantes del repo:
- separar payload externo por canal vs contrato interno canonico
- no usar herencia de DTO entre canales externos
- no usar Redis como fuente de verdad durable
- deduplicacion/auditoria durable en DynamoDB
- arquitectura de reintentos uniforme entre servicios con headers de control
- tenancy logica por `tenantId` con ruta de secretos por tenant/canal

---

Este archivo (`TECHNICAL_OVERVIEW.md`) es la vista consolidada de alto nivel tecnico de todo `aloChat/`.
