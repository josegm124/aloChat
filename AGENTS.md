# aloChat - Contexto de Proyecto

## Objetivo
Construir una plataforma omnicanal sobre AWS para recibir mensajes desde frontend propio, Telegram y Meta, normalizarlos a un contrato interno, procesarlos de forma idempotente, enriquecerlos con estado conversacional y responder usando RAG sobre AWS Bedrock.

## Decisiones base
- El ingreso de mensajes no publica directo a Kafka. Primero pasa por un adaptador de canal que valida firma/autenticidad, detecta canal, genera `messageId` interno e `idempotencyKey`, y normaliza el payload.
- Kafka se usa despues de la normalizacion, no antes.
- Redis es almacenamiento transitorio de estado conversacional y cache de proceso. No es el sistema de registro.
- La auditoria de mensajes e idempotencia debe vivir fuera de Redis. La opcion inicial recomendada es DynamoDB.
- DynamoDB y Redis no compiten en este proyecto: Redis resuelve velocidad/estado efimero; DynamoDB resuelve durabilidad/idempotencia/auditoria.
- El contrato interno debe usar composicion, no herencia profunda de DTOs. Usar un `Envelope` canonico y adaptadores por canal.
- La version por defecto es Java 21 con Spring Boot 3.x. Si una restriccion obliga Java 17, documentarla.
- Virtual threads son validas para consumidores y clientes IO-bound si el stack elegido ya es compatible.
- La opcion operativa por defecto en AWS es ECS Fargate. EKS solo si hay una necesidad real de Kubernetes.
- Kafka administrado recomendado: Amazon MSK. Para desarrollo local usar Docker Compose.
- IaC por defecto: CloudFormation modular. Si acelera el desarrollo, se permite CDK solo si termina sintetizando a CloudFormation y se documenta.

## Flujo objetivo
1. Canal externo envia webhook/request.
2. API Gateway expone endpoints publicos.
3. Servicio `inbound-adapter` valida y normaliza.
4. Servicio calcula `idempotencyKey` y registra estado inicial.
5. Publicacion a Kafka en topico normalizado.
6. Consumidores validan, enriquecen y cargan/actualizan estado en Redis.
7. Servicio AI ejecuta RAG en Bedrock.
8. Si RAG no responde con confianza suficiente, enruta al modelo Bedrock mas economico permitido por politicas del proyecto.
9. Servicio `outbound-dispatcher` formatea por canal y responde.
10. Estado final y metadatos de auditoria se persisten; el estado transitorio en Redis se limpia por TTL o cierre explicito.

## Contrato canonico minimo
Todo mensaje interno debe incluir:
- `messageId`
- `idempotencyKey`
- `traceId`
- `channel`
- `tenantId`
- `externalMessageId`
- `conversationId`
- `userId`
- `receivedAt`
- `content`
- `attachments`
- `metadata`
- `rawPayloadRef` opcional
- `status`

## Topicos iniciales recomendados
- `messages.ingress.normalized`
- `messages.processing.ai`
- `messages.outbound.dispatch`
- `messages.retry.short`
- `messages.retry.long`
- `messages.dlq`

Evitar crear topicos por cada micro-etapa sin una razon de aislamiento, throughput o cumplimiento.

## Observabilidad
- Metricas y dashboards: Prometheus + Grafana.
- Logs tecnicos: CloudWatch.
- Logs de negocio: emitir solo eventos finales y errores relevantes, no todo el payload completo.
- Todo mensaje debe propagar `traceId` y `messageId`.

## Estructura objetivo del repo
- `infra/` stacks CloudFormation, parametros y plantillas de despliegue.
- `services/inbound-adapter/` ingreso y normalizacion.
- `services/processor/` validacion, orquestacion y estado.
- `services/ai-orchestrator/` RAG + fallback Bedrock.
- `services/outbound-dispatcher/` respuesta por canal.
- `libs/message-contracts/` DTOs canonicos, eventos y utilidades compartidas.
- `local/` docker compose, configuracion local y semillas.
- `docs/` ADRs, diagramas y decisiones.

## Regla para futuras iteraciones
Antes de agregar codigo nuevo:
- Confirmar si el cambio pertenece a contrato canonico, adaptador de canal, procesamiento, AI o infraestructura.
- Mantener separadas las preocupaciones de canal externo y contrato interno.
- No usar Redis como fuente de verdad.
- No acoplar Bedrock a DTOs especificos de Telegram/Meta/Web.
- No crear pruebas, scaffolding de pruebas ni utilidades orientadas a tests antes del MVP.
- Los fixtures JSON por canal si se permiten desde el inicio porque forman parte del contrato funcional de entrada.
- El siguiente objetivo del MVP es canal Web desde Vercel hacia API Gateway con contrato controlado, validacion fuerte y secretos fuera del body.
- En este MVP, AWS Secrets Manager se reserva para la firma HMAC del canal Web.
- Telegram se trata como canal confiable de API para este MVP, pero sus tokens siguen fuera del repositorio.
- El corpus inicial real de negocio proviene del inventario de tienda y debe mantenerse normalizado dentro del repo para el MVP.
- La memoria conversacional durable se resume por `tenantId:channel:userId:conversationId`.
- Los futuros mensajes proactivos deben salir de `campaign hints` durables, no de Redis.
- En el MVP actual, los `campaign hints` se materializan dentro de `services/ai-orchestrator` mediante un scheduler pequeño; no existe aun un microservicio dedicado de campañas.
- OpenSearch Serverless es la opcion elegida para el vector store del MVP ampliado.
- El modelo de embeddings objetivo en `us-east-1` es `amazon.titan-embed-text-v2:0`.
- La estrategia vigente de tenancy es `multi-tenant logico`.
- La evolucion prevista es permitir `tenant premium` con stack dedicado cuando haga falta.
- Los secretos Web se resuelven por tenant con la ruta `alochat/{env}/tenants/{tenantId}/channels/web/ingress-signing-key`.
- Regla operativa permanente: no ejecutar el mismo comando mas de 2 veces seguidas si falla; en ese punto detenerse, reportar el bloqueo y pedir al usuario que corrija la parte operativa antes de insistir.

## Estado actual del MVP
- `Telegram` y `Web` ya entran por `API Gateway -> inbound-adapter -> Kafka -> processor -> ai-orchestrator -> outbound-dispatcher`.
- `OpenSearch Serverless` ya se usa para `kb_corpus` y `chat_memory`.
- `ai-orchestrator` ya aplica reranking heuristico para responder de forma conservadora y mas natural usando solo inventario/corpus.
- `ai-orchestrator` ya genera `campaign hints` durables y los procesa por scheduler para:
  - seguimiento por tiempo desde ultima consulta/proyecto
  - vigilancia de cambio de precio sobre productos de interes
- `Web` todavia no recibe push; para consumo de mensajes salientes y proactivos debe usar:
  - `GET /api/v1/web/messages/{messageId}`
  - `GET /api/v1/web/messages/conversation/{conversationId}?limit=20`

## Ingesta / Verificación RAG (OpenSearch)
### Verificar conteo actual del índice `kb_corpus`
```bash
python3 tools/knowledge/verify_count.py
```

Salida esperada (ejemplo):
```json
{"hits":{"total":{"value":50,"relation":"eq"}}}
```
