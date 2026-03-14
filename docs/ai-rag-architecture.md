# AI RAG y Memoria Conversacional

## Objetivo
Responder preguntas sobre el inventario real de la tienda y, al mismo tiempo, conservar memoria util por usuario o conversacion para futuras campañas.

Casos objetivo:
- pregunta directa de producto: `Tienes la pintura Vinil-Acrilica Premium y cuanto cuesta?`
- recomendacion: `Quiero pintar mi casa, que tipo de pintura usar?`
- seguimiento futuro: `Pintaste tu bodega hace 5 anos, quieres volver a comprar?`
- oportunidad comercial: `La pintura que preguntaste esta en descuento hoy`

## Separacion de conocimiento
Hay dos fuentes distintas y no se deben mezclar:

1. `knowledge corpus`
- conocimiento base del negocio
- inventario de tienda
- precios
- usos
- politicas de entrega
- politicas comerciales

2. `chat memory`
- resumen por usuario o chat
- productos de interes
- tags de interes
- fecha de ultima interaccion
- fecha candidata de seguimiento

## Claves de memoria
La memoria se acumula con una clave durable:

`tenantId:channel:userId:conversationId`

En Telegram, `conversationId` ya es `chat.id`, asi que el resumen queda amarrado al chat correcto.

## Implementacion actual
Hoy el `ai-orchestrator` ya hace esto:
- consulta un corpus local estructurado de inventario
- genera respuestas basadas en coincidencias del catalogo
- guarda memoria resumida en DynamoDB
- guarda hints de campaña en DynamoDB

Esto permite avanzar el MVP sin esperar OpenSearch ni Bedrock.

## Implementacion objetivo
La siguiente evolucion del `ai-orchestrator` es:

1. cargar el corpus a S3
2. indexarlo en OpenSearch Serverless
3. usar embeddings `amazon.titan-embed-text-v2:0`
4. consultar dos indices separados:
- `kb_corpus`
- `chat_memory`
5. construir el prompt final con:
- mensaje actual
- memoria resumida
- snippets del corpus

## Politica de memoria
No se embebe cada mensaje crudo.

Se conserva:
- resumen acumulado
- productos relacionados
- tags de interes
- trigger de recompra o repintado

Ejemplo:
- usuario comenta que quiere pintar su casa
- se recomienda pintura interior/exterior segun el caso
- se programa un hint de seguimiento a 5 anos

## Fuente actual del corpus
El inventario fuente que compartiste fue normalizado a:

- `services/ai-orchestrator/src/main/resources/knowledge/store_inventory_2026.psv`
- `services/ai-orchestrator/src/main/resources/knowledge/store_policies_2026.md`

El archivo externo de origen fue:
- `/home/jose-guerrero/Downloads/storage_inventory_50items_2026.txt`

## Roadmap tecnico
Completado:
- `ai-orchestrator` entre `processor` y `outbound-dispatcher`
- stub reemplazado por respuesta basada en catalogo
- memoria conversacional durable
- hints de campaña durable

Sigue:
1. OpenSearch Serverless real
2. carga del corpus a S3/OpenSearch
3. Bedrock embeddings Titan V2
4. retrieval hibrido
5. generacion final con Bedrock
