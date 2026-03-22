# laloResumen - Mapa tecnico integral de aloChat para exposicion de equipo

## 0) Como usar este documento

Este archivo esta pensado para 2 objetivos:

1. servir como guion tecnico para explicar el sistema de punta a punta.
2. servir como base estructurada para generar imagenes (Nano Banana) sin perder precision.

Recomendacion de exposicion:
- empieza con la vision de infraestructura (macro)
- baja a arquitectura por capas (aplicacion)
- explica el ciclo de vida de un mensaje (operacion)
- cierra con roadmap por fases y responsabilidades del team

---

## 1) Vision general del sistema (que problema resuelve)

`aloChat` es una plataforma omnicanal para:
- recibir mensajes de Web, Telegram y Meta
- normalizarlos a un contrato interno unico
- procesarlos con idempotencia y trazabilidad
- enriquecer contexto conversacional
- generar respuesta AI con conocimiento del negocio
- despachar respuesta por canal
- observar todo el flujo con metricas, logs, alertas y retries

Principios de arquitectura:
- separar payload externo por canal vs contrato interno canonico
- asincronia por eventos (Kafka/MSK)
- estado transitorio en Redis, estado durable en DynamoDB
- seguridad por tenant (HMAC + Secrets Manager)
- observabilidad desde el dia 1

---

## 2) Diagrama 1 - Infraestructura General (AWS + red + servicios)

### 2.1 Objetivo del diagrama
Mostrar como se conecta la infraestructura cloud completa, desde internet hasta servicios internos y stores, incluyendo seguridad y observabilidad.

### 2.2 Componentes (bloques obligatorios)

Zona Publica:
- Clientes: Browser Web, Telegram API, Meta API
- API Gateway (HTTP API)
- ALB (Application Load Balancer)
- WAF regional

Zona Privada (VPC / subnets privadas):
- ECS Fargate Cluster
  - inbound-adapter (8080)
  - processor (8081)
  - outbound-dispatcher (8082)
  - ai-orchestrator (8083)
- Service Discovery (Cloud Map)
- MSK Serverless (Kafka)
- ElastiCache Redis
- DynamoDB
  - idempotency
  - message-audit
  - conversation-memory
  - campaign-hints
- OpenSearch Serverless (coleccion vector)
  - kb_corpus
  - chat_memory
- Secrets Manager
  - secreto por tenant para HMAC Web
- S3 (knowledge corpus + raw payload bucket)

Observabilidad:
- CloudWatch Logs
- CloudWatch Alarms + SNS email
- Amazon Managed Prometheus (AMP)
- Amazon Managed Grafana (AMG)
- ECS collector (aws-otel-collector)

### 2.3 Conexiones/Flechas que deben verse

Ingreso:
- Browser/Telegram/Meta -> API Gateway
- API Gateway -> ALB -> inbound-adapter

Pipeline principal:
- inbound-adapter -> MSK topic `messages.ingress.normalized`
- processor consume `messages.ingress.normalized`
- processor -> MSK topic `messages.processing.ai`
- ai-orchestrator consume `messages.processing.ai`
- ai-orchestrator -> MSK topic `messages.outbound.dispatch`
- outbound-dispatcher consume `messages.outbound.dispatch`
- outbound-dispatcher -> Telegram API / Web channel / Meta channel

Data plane:
- inbound-adapter -> DynamoDB message-audit
- processor -> DynamoDB idempotency + message-audit
- processor -> Redis (estado conversacional corto)
- ai-orchestrator -> DynamoDB message-audit/conversation-memory/campaign-hints
- ai-orchestrator -> OpenSearch (`kb_corpus`, `chat_memory`)
- outbound-dispatcher -> DynamoDB message-audit
- outbound-dispatcher -> Redis cleanup

Security plane:
- inbound-adapter -> Secrets Manager (resolver HMAC key por tenant)
- servicios ECS -> IAM roles -> acceso controlado a MSK/DynamoDB/OpenSearch/Secrets

Observability plane:
- servicios ECS -> `/actuator/prometheus`
- collector ECS -> AMP remote_write (SigV4)
- CloudWatch <- logs de servicios
- Alarmas -> SNS email
- Grafana consume AMP + CloudWatch

### 2.4 Anotaciones tecnicas clave en el diagrama
- "Subnets privadas, tareas sin IP publica"
- "Throttling API Gateway + WAF"
- "Retry topics: short -> long -> DLQ"
- "Tenant-aware secrets"
- "Redis no es source of truth"

### 2.5 Prompt sugerido para Nano Banana (Diagrama 1)

"Create an enterprise cloud architecture diagram in Spanish, 16:9, clean professional style, no dark mode. Show Internet clients (Web Browser, Telegram API, Meta API) entering API Gateway, then ALB with WAF, then ECS Fargate services in private subnets: inbound-adapter(8080), processor(8081), ai-orchestrator(8083), outbound-dispatcher(8082). Add MSK Serverless Kafka with topics messages.ingress.normalized, messages.processing.ai, messages.outbound.dispatch, messages.retry.short, messages.retry.long, messages.dlq. Add DynamoDB tables idempotency, message-audit, conversation-memory, campaign-hints. Add ElastiCache Redis for transient conversation state. Add OpenSearch Serverless indices kb_corpus and chat_memory via VPC endpoint. Add Secrets Manager path alochat/dev/tenants/{tenantId}/channels/web/ingress-signing-key. Add CloudWatch Logs/Alarms/SNS, AMP, AMG, and ECS OTEL collector scraping /actuator/prometheus from all services. Use directional arrows and label security boundaries Public Zone and Private Zone." 

---

## 3) Diagrama 2 - Arquitectura por Capas (logica de aplicacion)

### 3.1 Objetivo del diagrama
Mostrar la correlacion entre codigo, tecnologia y responsabilidad funcional por capa.

### 3.2 Capas

Capa 1 - Channel Edge (entrada):
- controllers de inbound
- validadores por canal
- autenticacion HMAC web
- adaptadores Telegram/Meta/Web

Capa 2 - Canonical Messaging Core:
- `MessageEnvelope` y enums compartidos
- estrategia de IDs (`messageId`, `traceId`, `idempotencyKey`)
- metadata normalizada

Capa 3 - Event Transport & Retry Fabric:
- Kafka topics principales
- retry short/long
- DLQ
- headers de retry (`x-target-service`, etc.)

Capa 4 - Processing & State:
- deduplicacion durable (DynamoDB idempotency)
- estado transitorio (Redis)
- auditoria de estados (DynamoDB message-audit)

Capa 5 - AI Context & Generation:
- recuperacion de memoria conversacional
- retrieval de conocimiento (`kb_corpus`)
- generador de respuesta
- persistencia de memoria/hints

Capa 6 - Outbound Delivery:
- dispatchers por canal
- integracion real Telegram
- placeholders actuales Web/Meta
- cierre de estado `DISPATCHED`

Capa 7 - Observability & Ops:
- metricas, logs, alarmas
- dashboards
- trazabilidad por `traceId` y `messageId`

### 3.3 Prompt sugerido para Nano Banana (Diagrama 2)

"Create a layered architecture diagram in Spanish for aloChat, horizontal layers from top to bottom: 1) Channel Edge, 2) Canonical Messaging Core, 3) Event Transport & Retry Fabric, 4) Processing & State, 5) AI Context & Generation, 6) Outbound Delivery, 7) Observability & Ops. In each layer place concrete components: inbound controllers, validators, HMAC auth, channel adapters; MessageEnvelope and status model; Kafka topics including retry and DLQ; processor with DynamoDB idempotency and Redis state; ai-orchestrator with OpenSearch retrieval and conversation memory; outbound dispatchers with Telegram real integration and Web/Meta placeholders; CloudWatch+Prometheus+Grafana. Draw explicit arrows between layers and annotate why each connection exists." 

---

## 4) Diagrama 3 - Ciclo de vida de un mensaje (secuencia + estados)

### 4.1 Objetivo
Explicar claramente como cambia el `status` desde que entra un mensaje hasta que se entrega o falla.

### 4.2 Secuencia recomendada

1. `NORMALIZED` (inbound adapter)
2. `PUBLISHED` (publicado a `messages.ingress.normalized`)
3. `PROCESSING` (processor)
4. `AI_PROCESSING` (enrutado a AI)
5. `AI_COMPLETED` (respuesta generada)
6. `READY_FOR_DISPATCH` (publicado a outbound)
7. `DISPATCHED` (entrega final)

En errores:
- fallo primario -> `messages.retry.short`
- fallo short -> `messages.retry.long`
- fallo long -> `messages.dlq`

### 4.3 Prompt sugerido (Diagrama 3)

"Create a sequence diagram in Spanish showing message lifecycle in aloChat. Actors: Channel Client, inbound-adapter, Kafka, processor, ai-orchestrator, outbound-dispatcher, DynamoDB, Redis, OpenSearch. Show status transitions NORMALIZED -> PUBLISHED -> PROCESSING -> AI_PROCESSING -> AI_COMPLETED -> READY_FOR_DISPATCH -> DISPATCHED. Include retry branches to messages.retry.short, messages.retry.long, and messages.dlq. Annotate DynamoDB writes for audit/idempotency and Redis temporary state." 

---

## 5) Diagrama 4 - Mapa de datos (quien guarda que y por que)

### 5.1 Objetivo
Dejar claro el rol de cada DB/Store para evitar confusion entre equipos.

### 5.2 Matriz de almacenamiento

DynamoDB (durable, operativo):
- idempotency: evita reproceso de mensajes
- message-audit: historia de estados y consulta por mensaje/conversacion
- conversation-memory: resumen durable por memoria
- campaign-hints: disparadores de seguimiento y promociones

Redis (transitorio):
- estado conversacional corto
- aceleracion de proceso
- limpieza al finalizar despacho

OpenSearch Serverless (knowledge + retrieval):
- `kb_corpus`: productos, usos, precios, ofertas
- `chat_memory`: memoria indexada (segun adaptador activo)

S3:
- knowledge corpus source
- raw payloads (retencion controlada)

### 5.3 Prompt sugerido (Diagrama 4)

"Create a data architecture diagram in Spanish titled 'Persistencia aloChat'. Show DynamoDB tables (idempotency, message-audit, conversation-memory, campaign-hints) as durable operational storage; Redis as transient conversation state; OpenSearch Serverless indices kb_corpus and chat_memory as retrieval layer; S3 as corpus/raw payload storage. Add service-to-database arrows and labels 'por que se usa' and 'que guarda'. Include a warning box: Redis no es fuente de verdad." 

---

## 6) Diagrama 5 - Orden de desarrollo por fases (roadmap ejecutable)

### 6.1 Objetivo
Asignar trabajo al team en orden de dependencia real, evitando bloqueos.

### 6.2 Fases recomendadas

Fase 0 - Fundaciones:
- CloudFormation foundation
- red, SG, ECR, endpoints base

Fase 1 - Data plane:
- DynamoDB + Redis + buckets
- convenciones de nombres y parametros

Fase 2 - Mensajeria:
- MSK serverless
- topicos canonicos y politica retry/DLQ

Fase 3 - Inbound MVP:
- inbound-adapter web + HMAC
- normalizacion y publicacion a Kafka
- auditoria inicial

Fase 4 - Processor MVP:
- idempotencia + estado Redis
- enrutamiento a AI topic

Fase 5 - AI MVP:
- retrieval base
- generacion heuristica
- memoria + campaign hints

Fase 6 - Outbound MVP:
- Telegram real
- Web/Meta placeholders controlados
- cierre de flujo

Fase 7 - Front integration:
- polling de estado
- UX operativa para mensaje pendiente/final/error

Fase 8 - Observabilidad completa:
- CloudWatch + alarmas + SNS
- AMP/AMG + dashboards

Fase 9 - Hardening y escalado:
- WAF/throttling tuning
- runbooks, SLOs, QA end-to-end
- preparacion de tenant premium (si aplica)

### 6.3 Prompt sugerido (Diagrama 5)

"Create a phased roadmap diagram in Spanish with 10 phases (0 to 9) for aloChat implementation. Each phase must include objectives, key deliverables, and dependencies. Use timeline style, left to right, with color by domain: Infra, Data, Messaging, Backend, AI, Frontend, DevOps, QA. Emphasize dependency order and handoff points." 

---

## 7) Responsabilidades por equipo (para asignacion formal)

## 7.1 Infraestructura
Dueño de:
- `infra/foundation.yaml`, `infra/data.yaml`, `infra/msk.yaml`, `infra/runtime.yaml`, `infra/ai-rag.yaml`
Entregables:
- VPC/subnets/SG listos
- ECS runtime listo
- conectividad privada a stores
- outputs confiables para otros equipos
KPIs:
- despliegue reproducible
- cero drift critico

## 7.2 DevOps/SRE
Dueño de:
- pipelines build/deploy
- observabilidad (`observability.yaml`, `managed-observability.yaml`, collector)
- alarmas y runbooks
Entregables:
- release segura por ambiente
- paneles de salud y alertas accionables
- trazabilidad de incidentes
KPIs:
- MTTR
- tasa de despliegue exitoso

## 7.3 Backend Developers (Core)
Dueño de:
- `services/inbound-adapter`
- `services/processor`
- `services/outbound-dispatcher`
- `libs/message-contracts`
Entregables:
- contrato canonico estable
- flujo principal + retries + DLQ
- idempotencia funcional
KPIs:
- errores funcionales por release
- duplicados evitados

## 7.4 AI Developers
Dueño de:
- `services/ai-orchestrator`
- retrieval, ranking, memoria, hints
Entregables:
- respuestas correctas sobre corpus
- politicas de idioma
- evolucion a embeddings/generacion avanzada
KPIs:
- precision de respuesta
- cobertura de intents

## 7.5 Data / Knowledge Engineering
Dueño de:
- `tools/knowledge/*`
- calidad de corpus y promociones
- estrategia de versionado de conocimiento
Entregables:
- pipeline de ingesta repetible
- validacion de indices
- calidad semantica de metadata
KPIs:
- frescura del corpus
- tasa de errores de ingesta

## 7.6 Frontend
Dueño de:
- cliente web y experiencia de chat
- polling de estado
- i18n y manejo de estados de respuesta
Entregables:
- UX confiable para pending/final/error
- integracion estable con API interna
KPIs:
- errores de UI
- tiempo de respuesta percibido

## 7.7 QA
Dueño de:
- estrategia E2E por canal
- pruebas de resiliencia (retry, DLQ, timeout)
- validacion de contratos y seguridad
Entregables:
- matriz de casos por capa
- evidencia de no regresion
KPIs:
- defect leakage
- cobertura de caminos criticos

---

## 8) Matriz de pruebas recomendada por capa

Capa Inbound:
- firma HMAC valida/invalida
- payload invalido por canal
- tenant/header faltante

Capa Processing:
- mensaje nuevo vs duplicado
- persistencia de idempotencia
- fallos y salto a retry

Capa AI:
- retrieval sin resultados
- retrieval con ofertas
- idioma preferido `es/en`
- generacion con memoria previa

Capa Outbound:
- dispatch Telegram exitoso/fallido
- circuit breaker abierto
- limpieza de estado Redis

Capa Infra/Obs:
- health endpoints
- scraping prometheus
- alerta por error y latencia

---

## 9) Riesgos tecnicos principales y mitigaciones

Riesgo: mezclar responsabilidades de stores.
- mitigacion: regla estricta Redis transitorio, DynamoDB durable.

Riesgo: retrabajo por contratos ambiguos.
- mitigacion: `MessageEnvelope` como contrato unico.

Riesgo: incidentes silenciosos en canales.
- mitigacion: auditoria de estado + alarmas por servicio + DLQ monitoreada.

Riesgo: respuestas AI no confiables.
- mitigacion: respuestas conservadoras basadas en corpus + trazabilidad de snippets.

Riesgo: crecimiento sin control de costos.
- mitigacion: fases, SLOs, y tuning por dominio antes de escalar.

---

## 10) Guion recomendado para tu presentacion (orden narrativo)

1. Problema y objetivo de negocio (2-3 min)
2. Diagrama de infraestructura general (5-7 min)
3. Diagrama por capas de aplicacion (5-7 min)
4. Ciclo de vida de mensaje y resiliencia (5 min)
5. Mapa de datos y decisiones de persistencia (4-5 min)
6. Roadmap por fases (4-5 min)
7. Responsabilidades por equipo + handoffs (5 min)
8. Riesgos y controles (3 min)

---

## 11) Checklist para que cada imagen salga bien en Nano Banana

Antes de generar cada imagen, incluye siempre:
- titulo exacto del diagrama
- nivel (infra, capas, secuencia, datos, roadmap)
- lista explicita de componentes
- flechas con direccion y etiqueta
- leyenda de colores por dominio
- cajas de "notas criticas" (seguridad, retries, tenancy)
- idioma de la imagen: espanol tecnico

Parametros visuales recomendados:
- formato 16:9
- estilo enterprise claro
- tipografia legible
- no sobrecargar texto dentro de nodos
- agrupar por zonas y capas

---

## 12) Entrega para equipos (resumen corto por audiencia)

Infra/DevOps:
- foco en stacks, red privada, seguridad, despliegue, observabilidad

Backend:
- foco en contrato, topics, retries, status lifecycle, auditoria

AI/Data:
- foco en retrieval, memoria, corpus, calidad de datos, hints

Frontend:
- foco en endpoints de consulta, estados de chat, UX de asincronia

QA:
- foco en pruebas cross-layer y escenarios de falla real

---

Este documento esta listo para convertirlo en 4-6 imagenes tecnicas de alto nivel y para usarlo como guion de asignacion de responsabilidades por equipo.

---

## 13) Pipelines del proyecto (donde estan, como se hicieron y para que)

Este bloque complementa todo el resumen porque "pipeline" aqui existe en varios niveles: eventos de negocio, datos, observabilidad e infraestructura.

### 13.1 Pipeline de procesamiento de mensajes (pipeline core de negocio)

Donde esta:
- `services/inbound-adapter/*`
- `services/processor/*`
- `services/ai-orchestrator/*`
- `services/outbound-dispatcher/*`
- `libs/message-contracts/*`

Como se hizo:
- arquitectura event-driven con Kafka/MSK
- contrato canonico `MessageEnvelope`
- productores/consumidores por servicio usando Spring Kafka
- estados de mensaje y auditoria en DynamoDB
- retries por topics dedicados + DLQ

Para que sirve:
- desacoplar servicios
- soportar picos de trafico
- garantizar resiliencia ante fallas parciales
- mantener trazabilidad completa por `messageId` y `traceId`

### 13.2 Pipeline de reintentos y manejo de fallo (resiliencia)

Donde esta:
- topics: `messages.retry.short`, `messages.retry.long`, `messages.dlq`
- headers de control: `libs/message-contracts/src/main/java/com/alochat/contracts/message/RetryHeaders.java`
- orquestadores de retry por servicio:
  - `services/processor/.../MessageRetryOrchestrator.java`
  - `services/ai-orchestrator/.../AiRetryOrchestrator.java`
  - `services/outbound-dispatcher/.../OutboundRetryOrchestrator.java`

Como se hizo:
- cada servicio escucha su topic primario
- ante error publica a retry short con metadata de origen/target
- si vuelve a fallar pasa a retry long
- si vuelve a fallar pasa a DLQ

Para que sirve:
- evitar perdida de mensajes
- evitar bloqueos del flujo primario
- permitir remediacion operativa de casos fallidos

### 13.3 Pipeline de infraestructura (IaC)

Donde esta:
- `infra/foundation.yaml`
- `infra/data.yaml`
- `infra/observability.yaml`
- `infra/msk.yaml`
- `infra/tenant-config.yaml`
- `infra/runtime.yaml`
- `infra/ai-rag.yaml`
- `infra/managed-observability.yaml`
- parametros: `infra/parameters/dev/*.json`

Como se hizo:
- CloudFormation modular por dominios
- despliegue por orden de dependencias (foundation -> data -> ... -> managed-observability)
- inyeccion de parametros por ambiente (`dev`)

Para que sirve:
- reproducibilidad de ambientes
- trazabilidad de cambios infra
- reduccion de errores manuales de provisionamiento

### 13.4 Pipeline de build y empaquetado de servicios

Donde esta:
- `services/*/build.gradle`
- `tools/msk-admin/build.gradle`
- wrapper: `gradlew`, `gradle/wrapper/*`

Como se hizo:
- build multi-modulo Gradle
- Java 21 en todos los servicios
- plugin `jib` para crear imagenes sin Dockerfile por servicio

Para que sirve:
- compilar/validar artefactos
- publicar imagenes listas para ECS
- estandarizar runtime de todos los microservicios

Estado actual importante:
- no hay pipeline CI/CD automatizado en repo (`.github/workflows`, `Jenkinsfile`, `.gitlab-ci.yml` no existen)
- hoy el flujo de build/deploy es operativo/manual con comandos y AWS CLI

### 13.5 Pipeline de conocimiento (data/AI ingest)

Donde esta:
- script principal: `tools/knowledge/ingest_storage.py`
- verificacion: `tools/knowledge/verify_count.py`
- mapping memoria: `tools/knowledge/create_chat_memory_index.py`
- definiciones de tarea ECS: `aws/ecs/knowledge-ingest-task.json` y overrides en `aws/ecs/*.json`
- guia funcional: `docs/knowledge-pipeline.md`

Como se hizo:
- parseo de inventario fuente
- enriquecimiento por idioma (`es/en`) y tipo de doc (`product/offer`)
- firma SigV4 para escribir en OpenSearch Serverless
- ejecucion local o batch en ECS task ad-hoc

Para que sirve:
- mantener `kb_corpus` actualizado
- habilitar retrieval contextual del AI Orchestrator
- incorporar promociones vigentes en el motor de respuesta

### 13.6 Pipeline de observabilidad

Donde esta:
- collector config: `tools/observability/collector/collector-config.yaml`
- imagen collector: `tools/observability/collector/Dockerfile`
- stack managed: `infra/managed-observability.yaml`
- guia: `docs/managed-observability.md`

Como se hizo:
- scraping Prometheus de `/actuator/prometheus` en los 4 servicios
- procesamiento OTEL (`resource`, `batch`)
- remote_write a AMP con `sigv4auth`
- visualizacion en AMG + logs/alarms en CloudWatch/SNS

Para que sirve:
- monitoreo tecnico de latencia, errores, salud y consumo
- deteccion temprana de incidentes
- soporte para operaciones y SRE

### 13.7 Pipeline local de desarrollo (entorno reproducible)

Donde esta:
- `local/compose.yaml`
- `local/kafka/init-topics.sh`
- `local/prometheus/prometheus.yml`
- `local/grafana/provisioning/datasources/prometheus.yml`

Como se hizo:
- Docker Compose para levantar Kafka, Redis, Prometheus, Grafana
- bootstrap automatico de topics al iniciar

Para que sirve:
- pruebas de integracion local
- validacion de flujo sin depender de AWS en cada cambio
- onboarding mas rapido de developers/QA

### 13.8 Pipeline de seguridad en ingreso web

Donde esta:
- autenticacion HMAC: `services/inbound-adapter/.../WebRequestAuthenticationService.java`
- provider secreto: `services/inbound-adapter/.../SecretsManagerWebSigningSecretProvider.java`
- convencion de secreto: `infra/tenant-config.yaml`

Como se hizo:
- firma HMAC por tenant
- verificacion de timestamp (clock skew)
- resolucion de secreto en AWS Secrets Manager

Para que sirve:
- autenticidad e integridad del request web
- aislamiento multi-tenant por secreto

### 13.9 Prompt sugerido para imagen de pipelines

"Create a Spanish technical diagram titled 'Pipelines aloChat'. Show 8 parallel pipelines with lanes: (1) Message Processing Pipeline, (2) Retry/DLQ Pipeline, (3) Infrastructure IaC Pipeline, (4) Build & Image Pipeline, (5) Knowledge Ingestion Pipeline, (6) Observability Pipeline, (7) Local Dev Pipeline, (8) Web Security Pipeline. For each lane include three columns: 'Donde esta', 'Como se hizo', 'Para que sirve'. Add concrete repo paths and AWS services. Add note: 'CI/CD automatizado aun no definido en repo; despliegue actual operativo/manual con CloudFormation + AWS CLI'." 
