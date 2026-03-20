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
- consulta `kb_corpus` en OpenSearch Serverless
- consulta y actualiza `chat_memory`
- aplica reranking heuristico por tipo de superficie o uso
- genera respuestas conservadoras basadas solo en inventario/corpus
- guarda memoria resumida en DynamoDB/OpenSearch segun el adaptador activo
- guarda hints de campaña en DynamoDB
- ejecuta un scheduler interno para materializar campañas:
  - seguimiento por tiempo
  - vigilancia de cambio de precio

Esto ya cubre el MVP funcional sin depender todavia de Bedrock para generacion final.

## Implementacion objetivo
La siguiente evolucion del `ai-orchestrator` es:

1. usar embeddings `amazon.titan-embed-text-v2:0`
2. retrieval mas rico sobre:
- `kb_corpus`
- `chat_memory`
3. construir el prompt final con:
- mensaje actual
- memoria resumida
- snippets del corpus
4. usar Bedrock para redaccion final cuando agregue valor sin salir del inventario/corpus

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
- respuestas basadas en inventario real
- memoria conversacional durable
- hints de campaña durable
- OpenSearch Serverless real para conocimiento y memoria
- scheduler MVP de campañas

Sigue:
1. Bedrock embeddings Titan V2
2. retrieval hibrido
3. generacion final con Bedrock
