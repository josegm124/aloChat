# Pipeline de Conocimiento

## Corpus inicial del negocio
El corpus de la tienda se compone de:
- inventario de productos
- categoria o tipo de pintura
- precio
- uso recomendado
- politicas de entrega
- politicas comerciales

## Fuente de origen
Archivo original compartido por el usuario:
- `/home/jose-guerrero/Downloads/storage_inventory_50items_2026.txt`

Version normalizada dentro del repo:
- `services/ai-orchestrator/src/main/resources/knowledge/store_inventory_2026.psv`

## Flujo objetivo de ingestion
1. tomar el PSV del repo o una version mas reciente
2. convertirlo a documentos por producto
3. agregar metadata:
- `itemId`
- `productName`
- `category`
- `priceMxn`
- `usage`
- `tenantId`
- `sourceVersion`
4. subir documentos a S3
5. indexarlos en `kb_corpus`
6. exponer retrieval al `ai-orchestrator`

## Flujo objetivo de memoria
1. llega consulta
2. se obtiene `memoryKey`
3. se recupera resumen conversacional
4. se consulta `kb_corpus`
5. se responde
6. se actualiza `chat_memory`
7. se guarda hint de campaña si aplica

## Reglas practicas
- no guardar cada mensaje como embedding
- no mezclar indices de corpus y memoria
- no meter secretos en el body de requests
- no usar Redis como fuente de verdad para memoria larga
