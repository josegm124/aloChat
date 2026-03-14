# Tenant Model

## Estrategia actual
La estrategia actual es `multi-tenant logico`.

Eso significa:
- un solo runtime compartido
- un solo `inbound-adapter`
- un solo `processor`
- un solo `ai-orchestrator`
- un solo `outbound-dispatcher`
- separacion por `tenantId` en contratos, datos, secretos e indices

## Claves de separacion
- `tenantId` obligatorio en Web
- secretos por tenant y canal
- `memoryKey` con `tenantId`
- indices vectoriales con prefijo o alias por tenant
- tablas DynamoDB con claves que incluyen `tenantId`

## Estrategia recomendada por capa
### Web ingress
- header obligatorio `x-tenant-id`
- HMAC resuelto por secreto del tenant

### DynamoDB
- usar `tenantId` como atributo obligatorio de negocio
- para futuras optimizaciones, agregar GSIs por `tenantId` si el acceso operacional lo requiere

### OpenSearch
- preferir alias o prefijos por tenant:
  - `kb_corpus_{tenantId}`
  - `chat_memory_{tenantId}`
- si varios tenants comparten runtime, no mezclar documentos sin metadata fuerte de tenant

### Redis
- prefijos por tenant en keys
- nunca usar Redis como registro durable multi-tenant

## Evolucion futura
Despues del `multi-tenant logico`, el siguiente nivel es `tenant premium` con stack dedicado si hace falta.

Cuándo conviene:
- cumplimiento regulatorio
- alto volumen de trafico
- modelos o corpus muy distintos
- necesidad contractual de aislamiento fuerte

## Tenant premium
Un tenant premium podria tener:
- su propio stack CloudFormation
- su propio OpenSearch collection
- sus propias tablas o cuentas de datos
- sus propios secretos
- su propio WAF y limites

## Decision vigente
- ahora: `multi-tenant logico`
- despues: permitir `tenant premium` con stack dedicado si hace falta
