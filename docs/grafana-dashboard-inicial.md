# Grafana Dashboard Inicial

Estado validado en AMP el 2026-03-19:

- `up`
- `http_server_requests_seconds_count`
- `jvm_memory_used_bytes`
- `process_cpu_usage`
- `system_cpu_usage`
- `jvm_threads_live_threads`
- `kafka_consumer_fetch_manager_records_consumed_total`
- `kafka_consumer_fetch_manager_records_lag_max`

No validado hoy en este despliegue:

- `kafka_producer_record_send_total`

## Data source

En Amazon Managed Grafana usa un data source de tipo `Amazon Managed Prometheus` apuntando al workspace:

- `ws-296a1b72-cbc3-430b-9ffe-1282e63e0efe`

## Paneles PromQL

### 1. Estado por servicio

```promql
up{project="alochat"}
```

### 2. Servicios caidos

```promql
sum by (service) (up{project="alochat"} == 0)
```

### 3. Requests por segundo

```promql
sum by (service) (
  rate(http_server_requests_seconds_count{project="alochat"}[5m])
)
```

### 4. Requests por status

```promql
sum by (service, status) (
  rate(http_server_requests_seconds_count{project="alochat"}[5m])
)
```

### 5. Error rate 5xx

```promql
sum by (service) (
  rate(http_server_requests_seconds_count{project="alochat",status=~"5.."}[5m])
)
```

### 6. Latencia promedio HTTP

```promql
sum by (service) (
  rate(http_server_requests_seconds_sum{project="alochat"}[5m])
)
/
sum by (service) (
  rate(http_server_requests_seconds_count{project="alochat"}[5m])
)
```

### 7. Heap usado por servicio

```promql
sum by (service) (
  jvm_memory_used_bytes{project="alochat",area="heap"}
)
```

### 8. Porcentaje de heap usado

```promql
100 *
sum by (service) (
  jvm_memory_used_bytes{project="alochat",area="heap"}
)
/
sum by (service) (
  jvm_memory_max_bytes{project="alochat",area="heap"}
)
```

### 9. Threads vivos por servicio

```promql
sum by (service) (
  jvm_threads_live_threads{project="alochat"}
)
```

### 10. CPU del proceso

```promql
avg by (service) (
  process_cpu_usage{project="alochat"}
)
```

### 11. CPU del sistema

```promql
avg by (service) (
  system_cpu_usage{project="alochat"}
)
```

### 12. Throughput de consumidores Kafka

```promql
sum by (service) (
  rate(kafka_consumer_fetch_manager_records_consumed_total{project="alochat"}[5m])
)
```

### 13. Lag maximo Kafka por servicio

```promql
max by (service) (
  kafka_consumer_fetch_manager_records_lag_max{project="alochat"}
)
```

## Paneles que deben ir por CloudWatch, no por AMP

Estos no los intentaria resolver por PromQL en este MVP:

- `Redis`: conexiones, CPU, memoria, evictions
- `OpenSearch Serverless`: throttling, latencia, errores, capacidad
- `API Gateway`: latencia p95, 4xx, 5xx
- `MSK Serverless`: bytes in/out, throttling, conexiones

Usa el data source `CloudWatch` para esos paneles.

## Dashboard recomendado

Orden inicial:

1. `Service health`
2. `HTTP throughput`
3. `HTTP errors`
4. `HTTP latency`
5. `JVM heap`
6. `CPU`
7. `Threads`
8. `Kafka consume rate`
9. `Kafka lag`
10. `Infra AWS` via CloudWatch

## Nota operativa

Durante esta correccion se aplicaron hotfixes manuales en ECS para:

- collector de observabilidad
- `processor`
- `ai-orchestrator`

Cuando el stack `alochat-dev-managed-observability` y el `runtime` queden libres, conviene reconciliar CloudFormation con esos cambios para eliminar deriva.
