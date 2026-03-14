# Contratos de Entrada MVP

## Decision
No vamos a usar un DTO padre con herencia para Telegram, Meta y Web.

Vamos a usar:
- contrato canonico interno unico: `MessageEnvelope`
- DTO externo especifico por canal
- composicion en los DTOs del canal Web
- adapters por canal para traducir al contrato interno

## Por que no herencia
Telegram, Meta y Web no representan la misma jerarquia natural. Si intentamos forzarlos a un padre comun externo:
- el DTO base se infla con campos opcionales irrelevantes
- se mezclan detalles del canal con el core del sistema
- la evolucion de un canal rompe a los otros

La herencia correcta aqui esta en el comportamiento del adapter, no en el shape del request externo.

## Contrato recomendado para Web desde Vercel
El frontend Web si lo controlamos, por lo tanto aqui si conviene definir un request limpio:

```json
{
  "tenantId": "acme",
  "messageId": "web-msg-0001",
  "conversationId": "conv-123",
  "user": {
    "id": "user-42",
    "role": "customer",
    "name": "Eduardo Perez"
  },
  "message": {
    "type": "text",
    "text": "Hola bot"
  },
  "context": {
    "source": "vercel-web",
    "locale": "es-MX",
    "client": {
      "app": "alochat-web",
      "version": "1.0.0",
      "platform": "web"
    },
    "session": {
      "id": "sess-777",
      "anonymousId": "anon-123"
    }
  },
  "metadata": {
    "correlationId": "corr-001",
    "page": "/checkout",
    "referrer": "https://alochat.vercel.app"
  }
}
```

## Mapeo conceptual con Telegram
Telegram trae datos que no controlamos:
- `message.message_id` -> `externalMessageId`
- `message.chat.id` -> `conversationId`
- `message.from.id` -> `userId`
- `message.text` -> `content.text`

Web trae datos que si controlamos:
- `messageId` -> `externalMessageId`
- `conversationId` -> `conversationId`
- `user.id` -> `userId`
- `user.role` -> `metadata.userRole`
- `message.text` -> `content.text`
- `context.*` -> `metadata` y contexto operativo

## Seguridad entre Vercel y API Gateway
No mandar secretos en el body.

Usar headers:
- `x-channel: web`
- `x-tenant-id: acme`
- `x-trace-id: <uuid>`
- `x-signature: <hex hmac sha256 de x-timestamp + "." + rawBody>`
- `x-timestamp: <epoch seconds>`

El secreto se resuelve por tenant con esta ruta:
- `alochat/{env}/tenants/{tenantId}/channels/web/ingress-signing-key`

La recomendacion para el MVP:
- el frontend de Vercel llama a un backend seguro o edge function propia
- la firma HMAC se calcula en entorno seguro con variables de entorno
- API Gateway o `inbound-adapter` valida firma y ventana de tiempo

## Flujo asincrono recomendado para Web
La decision del MVP es asincrona.

### Paso 1
Vercel envia:
- `POST /api/v1/inbound/web`

### Paso 2
El backend responde `202 Accepted`:

```json
{
  "messageId": "d4a0b947-4a26-4b59-a062-0f8c0f6db786",
  "idempotencyKey": "web:acme:web-msg-0001",
  "channel": "WEB",
  "status": "PUBLISHED"
}
```

### Paso 3
El frontend consulta estado:
- `GET /api/v1/web/messages/{messageId}`

Respuesta ejemplo:
```json
{
  "messageId": "d4a0b947-4a26-4b59-a062-0f8c0f6db786",
  "conversationId": "conv-123",
  "channel": "WEB",
  "status": "PROCESSING",
  "updatedAt": "2026-03-10T23:00:00Z",
  "contentText": "Hola bot"
}
```

Estados esperados en este tramo:
- `PUBLISHED`
- `PROCESSING`
- `READY_FOR_DISPATCH`
- `DISPATCHED`

Para el MVP, polling es mejor que WebSocket o SSE porque:
- simplifica Vercel + API Gateway
- reduce complejidad operativa
- nos deja cerrar el flujo principal antes de tiempo real push

## Secretos a usar despues
Cuando integremos Telegram y Meta:
- tokens en AWS Secrets Manager o SSM Parameter Store
- nunca en repositorio
- nunca en fixtures
- variables de entorno solo como referencia al secreto o inyeccion controlada en runtime
