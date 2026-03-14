# aloChat

Base de arquitectura para una plataforma omnicanal en AWS con Java, Kafka, Redis y Bedrock.

## Evaluacion del diseno
La idea general es buena: ingreso omnicanal, normalizacion, procesamiento asincrono, estado conversacional, AI y observabilidad. Los ajustes criticos son estos:

1. No publiques a Kafka directamente desde API Gateway. Agrega una capa `inbound-adapter` antes de Kafka para validar firmas, detectar canal, generar IDs internos y deduplicar.
2. No modeles todo con un DTO padre grande. Usa un `Envelope` canonico y adaptadores por canal; la variacion de Meta, Telegram y Web debe quedarse en el borde.
3. No dependas de Redis para auditoria o deduplicacion duradera. Redis sirve para estado conversacional temporal; para idempotencia y registro de mensajes usa DynamoDB.
4. No multipliques topicos por cada detalle del flujo desde el inicio. Arranca con pocos topicos canonicos, mas `retry` y `dlq`.
5. No dejes el modelo "mas economico" como una decision rigida. Define una politica de ruteo por costo, latencia y calidad.

## Arquitectura base recomendada
```text
Frontend Web / Telegram / Meta
            |
        API Gateway
            |
      inbound-adapter
  (auth, channel detection,
   normalization, idempotency)
            |
      Kafka / Amazon MSK
            |
        processor
   (validation, enrichment,
    Redis state handling)
            |
      ai-orchestrator
   (stub ahora, Bedrock despues)
            |
    outbound-dispatcher
            |
   Web / Telegram / Meta APIs
```

Servicios de apoyo:
- DynamoDB para idempotencia y auditoria.
- ElastiCache Redis para estado conversacional temporal.
- S3 para payloads crudos o evidencia si hace falta.
- Prometheus, Grafana y CloudWatch para observabilidad.

Redis y DynamoDB no cumplen el mismo objetivo:
- Redis mantiene contexto conversacional y datos efimeros de trabajo.
- DynamoDB guarda lo que no se debe perder cuando Redis expire, reinicie o se limpie.

## Stack recomendado
- Java 21
- Spring Boot 3.x
- Amazon MSK
- Amazon ECS Fargate
- API Gateway
- DynamoDB
- ElastiCache for Redis
- AWS Bedrock
- CloudFormation
- Docker Compose para entorno local

`EKS` y `Lambda` quedan como opcionales. Mi recomendacion inicial es:
- `Lambda` solo si quieres un webhook ingress muy simple y bursty.
- `EKS` solo si ya tienes madurez operativa fuerte en Kubernetes.

## Contrato interno sugerido
El mensaje interno debe separar metadatos del contenido:

```java
public record MessageEnvelope(
    String messageId,
    String idempotencyKey,
    String traceId,
    Channel channel,
    String tenantId,
    String externalMessageId,
    String conversationId,
    String userId,
    Instant receivedAt,
    NormalizedContent content,
    List<Attachment> attachments,
    Map<String, String> metadata,
    String rawPayloadRef,
    MessageStatus status
) {}
```

Cada canal debe tener su adaptador:
- `TelegramInboundAdapter`
- `MetaInboundAdapter`
- `WebInboundAdapter`

## Orden de construccion recomendado
1. Definir contrato canonico, estrategia de IDs e idempotencia.
2. Crear monorepo base y estructura de modulos.
3. Levantar entorno local con Docker Compose para Kafka, Redis, Prometheus y Grafana.
4. Implementar `inbound-adapter` con un canal primero, idealmente Web.
5. Implementar publicacion/consumo Kafka y politicas de `retry` y `dlq`.
6. Implementar estado conversacional en Redis y auditoria en DynamoDB.
7. Integrar Telegram y Meta reutilizando el mismo contrato interno.
8. Construir `ai-orchestrator` con un stub primero y Bedrock despues.
9. Agregar RAG con una base de conocimiento real y memoria conversacional resumida.
10. Cerrar con dashboards, alertas, trazabilidad y endurecimiento operativo.

## Enfoque actual de MVP
El MVP mas cercano se enfoca en `Vercel -> API Gateway -> inbound-adapter`.

Reglas para este tramo:
- Web usa contrato propio controlado por nosotros.
- Telegram y Meta conservan su payload externo original y se adaptan en el borde.
- No usar DTO padre por herencia entre canales externos.
- Los secretos de integracion viajan por headers/variables de entorno, no en el body.

Para Telegram real:
- Telegram se tratara como canal confiable en este MVP.
- AWS Secrets Manager se usara para la firma HMAC del canal Web.

## Estado actual de AI
- `ai-orchestrator` ya no responde solo con placeholder.
- Usa un corpus local del inventario 2026 de la tienda para responder preguntas de producto, precio y recomendacion.
- Guarda memoria resumida por `tenantId:channel:userId:conversationId`.
- Guarda hints de campaña para recompra o descuentos futuros.
- El siguiente salto es mover retrieval a OpenSearch Serverless y generacion a Bedrock.

## Tenancy actual
- ahora: multi-tenant logico
- despues: permitir tenant premium con stack dedicado si hace falta

El hardening actual ya asume:
- secretos Web por tenant en Secrets Manager
- aislamiento logico por `tenantId`
- endurecimiento de red e ingreso con WAF, throttling y subnets privadas

## Resiliencia actual
- idempotencia durable en DynamoDB
- retry corto y largo sobre Kafka
- DLQ sobre Kafka
- circuit breaker y retry HTTP para Telegram outbound

## Configuracion de Codex que acelera el desarrollo
1. Mantener este `AGENTS.md` actualizado con decisiones de arquitectura, limites y convenciones.
2. Crear ADRs cortos en `docs/` para decisiones grandes: Kafka topics, Redis strategy, Bedrock routing, deployment model.
3. Definir scripts estables desde el inicio:
   - `./gradlew build`
   - `./gradlew :services:inbound-adapter:bootRun`
   - `docker compose -f local/compose.yaml up -d`
4. Separar pronto los contratos compartidos en `libs/message-contracts/`.
5. Tener fixtures JSON reales por canal para validar adaptadores y mappings.
6. Mantener ejemplos de requests reales anonimizados para no reinventar contratos en cada iteracion.

## Regla operativa actual
Durante el arranque del MVP no se crean pruebas ni scaffolding de pruebas. En esta etapa el foco es:
- contrato canonico
- adapters por canal
- publicacion base
- entorno local
- observabilidad minima

Los tests se agregan despues de validar el flujo principal.

## Estructura inicial actual
```text
aloChat/
├── libs/message-contracts
├── services/inbound-adapter
├── services/processor
├── services/ai-orchestrator
├── services/outbound-dispatcher
├── fixtures/channels
└── local
```

## Comandos locales previstos
- `docker compose -f local/compose.yaml up -d`
- `./gradlew :services:inbound-adapter:bootRun`

## Primer entregable tecnico recomendado
Si arrancamos correctamente, el primer hito debe ser este:
- `inbound-adapter` expuesto por API Gateway
- normalizacion del canal Web
- deduplicacion basica
- publicacion a Kafka
- consumidor base
- Redis y DynamoDB conectados
- dashboard minimo en Grafana

Con eso ya validas la columna vertebral del sistema antes de meter Bedrock, Telegram o Meta.
