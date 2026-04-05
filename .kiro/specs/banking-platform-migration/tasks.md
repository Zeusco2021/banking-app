# Plan de Implementación: Migración de Plataforma Bancaria a Microservicios

## Descripción General

Implementación incremental de la plataforma bancaria sobre AWS usando Spring Boot 3 / Java 21, siguiendo el patrón Strangler Fig. Cada tarea construye sobre la anterior, comenzando por la infraestructura base y terminando con el cableado completo de todos los microservicios.

## Tareas

- [x] 1. Configurar estructura base del proyecto y módulos Maven
  - Crear proyecto multi-módulo Maven con módulos: `api-gateway-config`, `auth-service`, `account-service`, `transaction-service`, `notification-service`, `audit-service`, `legacy-adapter`, `shared-lib`
  - Definir en `shared-lib` las interfaces Java (`AuthService`, `AccountService`, `TransactionService`, `NotificationService`, `AuditService`, `LegacyAdapterService`) y los modelos de datos (`Account`, `Transaction`, `AuditEvent`, `TokenResponse`, `TransactionResult`)
  - Configurar `pom.xml` raíz con dependencias: Spring Boot 3.2+, Java 21, Resilience4j 2.1+, Micrometer 1.12+, jqwik 1.8+, Testcontainers 1.19+
  - Habilitar Virtual Threads en cada microservicio con `VirtualThreadConfig` (bean `TomcatProtocolHandlerCustomizer` + `ExecutorService`)
  - _Requisitos: 4.8, 9.5_

- [x] 2. Implementar infraestructura compartida y configuración de seguridad
  - [x] 2.1 Implementar configuración mTLS con Istio y TLS 1.3
    - Crear `IstioMtlsConfig` con `PeerAuthentication` y `DestinationRule` en modo STRICT
    - Configurar `application.yml` de cada servicio para TLS 1.3 en endpoints externos
    - _Requisitos: 12.1, 12.2_

  - [x] 2.2 Implementar integración con AWS Secrets Manager
    - Crear `SecretsManagerConfig` usando Spring Cloud AWS para inyectar credenciales en runtime
    - Garantizar que ninguna credencial se almacena en variables de entorno ni código fuente
    - _Requisitos: 12.3_

  - [x] 2.3 Implementar propagación de `correlationId`
    - Crear `CorrelationIdFilter` (servlet filter) que genera o propaga el `correlationId` en headers de entrada y salida
    - Integrar con MDC de SLF4J para incluirlo en todos los logs
    - _Requisitos: 1.7, 11.2_

  - [ ]* 2.4 Escribir test de propiedad para propagación de correlationId
    - **Propiedad 16: Propagación de correlationId**
    - **Valida: Requisitos 1.7, 11.2**

- [x] 3. Implementar Auth Service
  - [x] 3.1 Implementar autenticación OAuth2/JWT con Redis
    - Escribir `AuthServiceImpl.authenticate()`: verificar intentos fallidos en Redis, validar credenciales con BCrypt, generar `accessToken` (RS256, TTL 15 min) y `refreshToken` (TTL 7 días), cachear en Redis, registrar `AuditEvent`
    - Implementar bloqueo temporal tras 5 intentos fallidos (HTTP 423, TTL 15 min en Redis)
    - _Requisitos: 2.1, 2.2, 2.3, 2.8_

  - [x] 3.2 Implementar validación, revocación y refresh de tokens
    - Escribir `validateToken()`: verificar primero en caché Redis antes de cualquier operación adicional
    - Escribir `revokeToken()`: eliminar entrada de Redis de forma inmediata
    - Escribir `refreshToken()`: emitir nuevo `accessToken` sin re-autenticación
    - Implementar respuesta HTTP 401 con header `WWW-Authenticate: Bearer error="token_expired"` para tokens expirados
    - _Requisitos: 2.4, 2.5, 2.6, 2.7_

  - [x] 3.3 Implementar rotación de claves JWT cada 90 días
    - Crear `JwtKeyRotationService` que carga la clave activa y la anterior desde Secrets Manager
    - Garantizar que tokens emitidos con la clave anterior siguen siendo válidos hasta su expiración
    - _Requisitos: 12.7_

  - [ ]* 3.4 Escribir test de propiedad para autenticación sin bypass
    - **Propiedad 9: Autenticación sin bypass**
    - **Valida: Requisitos 1.5, 2.7**

  - [ ]* 3.5 Escribir test de propiedad para round-trip de refresh token
    - **Propiedad 10: Round-trip de autenticación con refresh token**
    - **Valida: Requisitos 2.1, 2.4**

  - [ ]* 3.6 Escribir test de propiedad para revocación inmediata de tokens
    - **Propiedad 11: Revocación inmediata de tokens**
    - **Valida: Requisitos 2.6**

  - [ ]* 3.7 Escribir test de propiedad para rotación de claves sin interrupción
    - **Propiedad 19: Rotación de claves JWT sin interrupción de sesiones**
    - **Valida: Requisitos 12.7**

- [ ] 4. Punto de control — Verificar que todos los tests pasan
  - Asegurarse de que todos los tests pasan. Consultar al usuario si surgen dudas.

- [x] 5. Implementar Account Service
  - [x] 5.1 Implementar creación y consulta de cuentas con caché Redis
    - Escribir `AccountServiceImpl.createAccount()`: persistir en Oracle DB, retornar `Account` con `accountId` generado (UUID inmutable)
    - Escribir `getAccount()` y `getBalance()`: consultar primero caché Redis (TTL 30s), luego Oracle DB en cache miss
    - Escribir `updateAccount()` y `closeAccount()`: invalidar caché Redis de forma inmediata tras modificación
    - Implementar `ShardRouter.getShardKey()` usando `Math.abs(accountId.hashCode()) % NUM_SHARDS`
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.7_

  - [x] 5.2 Implementar validaciones de negocio en Account Service
    - Rechazar saldo negativo en cuentas CHECKING/SAVINGS con HTTP 422
    - Rechazar código de moneda no ISO 4217 con HTTP 400
    - Garantizar procesamiento independiente al cerrar múltiples cuentas del mismo cliente
    - _Requisitos: 3.5, 3.6, 3.8_

  - [ ]* 5.3 Escribir test de propiedad para inmutabilidad del accountId
    - **Propiedad 14: Inmutabilidad del accountId**
    - **Valida: Requisitos 3.7**

  - [ ]* 5.4 Escribir test de propiedad para invariante de saldo no negativo
    - **Propiedad 13: Invariante de saldo no negativo**
    - **Valida: Requisitos 3.5**

  - [ ]* 5.5 Escribir test de propiedad para validación de moneda ISO 4217
    - **Propiedad 15: Validación de moneda ISO 4217**
    - **Valida: Requisitos 3.6**

- [x] 6. Implementar Transaction Service con Virtual Threads y Outbox Pattern
  - [x] 6.1 Implementar procesamiento de transacciones con locks distribuidos
    - Escribir `TransactionServiceImpl.processTransaction()` usando `CompletableFuture.supplyAsync` con `virtualThreadExecutor`
    - Implementar verificación de idempotencia (`idempotencyStore.exists/store`) antes de procesar
    - Adquirir lock distribuido en Redis (`SETNX`) sobre `sourceAccountId` antes de modificar saldos
    - Ejecutar débito y crédito en transacción DB atómica; invalidar caché Redis de saldo de ambas cuentas
    - _Requisitos: 4.1, 4.2, 4.6, 4.8, 4.9_

  - [x] 6.2 Implementar Outbox Pattern para publicación de eventos Kafka
    - Publicar `TransactionCompleted` en topic `transactions.completed` de forma asíncrona tras commit DB
    - Si Kafka no está disponible, persistir evento en tabla `OUTBOX` de Oracle DB
    - Implementar `OutboxProcessor`: proceso periódico (`@Scheduled`) que reintenta publicar eventos pendientes hasta éxito
    - _Requisitos: 4.4, 4.5, 10.6_

  - [x] 6.3 Implementar reversión de transacciones y rechazo por saldo insuficiente
    - Escribir `reverseTransaction()`: crear transacción tipo `REVERSAL`, restaurar saldos de forma atómica
    - Rechazar transacciones con saldo insuficiente con HTTP 422 y código `INSUFFICIENT_FUNDS` sin modificar saldos
    - _Requisitos: 4.3, 4.7_

  - [x] 6.4 Implementar ShardRouter determinístico para Oracle DB
    - Escribir `ShardRouter.resolveDataSource()` usando `Math.abs(accountId.hashCode()) % NUM_SHARDS`
    - Garantizar que el mismo `accountId` siempre resuelve al mismo `DataSource`
    - _Requisitos: 4.10_

  - [ ]* 6.5 Escribir test de propiedad para conservación de saldo
    - **Propiedad 1: Conservación de saldo en transacciones**
    - **Valida: Requisitos 4.2**

  - [ ]* 6.6 Escribir test de propiedad para idempotencia de transacciones
    - **Propiedad 2: Idempotencia de transacciones**
    - **Valida: Requisitos 4.1**

  - [ ]* 6.7 Escribir test de propiedad para rechazo con saldo insuficiente
    - **Propiedad 3: Rechazo de transacciones con saldo insuficiente**
    - **Valida: Requisitos 4.3**

  - [ ]* 6.8 Escribir test de propiedad para reversión round-trip
    - **Propiedad 4: Reversión restaura saldos originales (round-trip)**
    - **Valida: Requisitos 4.7**

  - [ ]* 6.9 Escribir test de propiedad para determinismo de sharding
    - **Propiedad 5: Determinismo de sharding**
    - **Valida: Requisitos 4.10**

  - [ ]* 6.10 Escribir test de propiedad para publicación de eventos Kafka
    - **Propiedad 17: Publicación de eventos Kafka por transacción completada**
    - **Valida: Requisitos 4.4, 4.5**

  - [ ]* 6.11 Escribir test de propiedad para procesamiento eventual del OUTBOX
    - **Propiedad 18: Procesamiento eventual de eventos OUTBOX**
    - **Valida: Requisitos 10.6**

- [ ] 7. Punto de control — Verificar que todos los tests pasan
  - Asegurarse de que todos los tests pasan. Consultar al usuario si surgen dudas.

- [x] 8. Implementar Notification Service y Audit Service
  - [x] 8.1 Implementar Notification Service como consumidor Kafka
    - Crear `NotificationServiceImpl` con `@KafkaListener` en topics de eventos de transacciones y cuentas
    - Implementar envío por canal (email vía AWS SES, SMS/push vía AWS SNS) y persistir resultado en MongoDB (`SENT`/`FAILED`)
    - Implementar reintento con backoff exponencial si AWS SES/SNS no está disponible antes de marcar como `FAILED`
    - Garantizar operación completamente asíncrona sin bloquear el path crítico
    - _Requisitos: 5.1, 5.2, 5.3, 5.4_

  - [x] 8.2 Implementar Audit Service con persistencia append-only en MongoDB
    - Crear `AuditServiceImpl.recordEvent()`: persistir `AuditEvent` en MongoDB con todos los campos requeridos (`eventId`, `correlationId`, `serviceOrigin`, `action`, `actorId`, `resourceId`, `payload`, `ipAddress`, `timestamp`)
    - Configurar índice único en MongoDB para prevenir actualizaciones y borrados (colección con `validator` y sin permisos de update/delete)
    - Implementar `queryEvents()` con filtrado por `correlationId`, `actorId`, `resourceId` y rango de `timestamp`
    - _Requisitos: 6.1, 6.2, 6.3, 6.4_

  - [ ]* 8.3 Escribir test de propiedad para inmutabilidad de auditoría
    - **Propiedad 7: Inmutabilidad de auditoría**
    - **Valida: Requisitos 6.3**

  - [ ]* 8.4 Escribir test de propiedad para completitud de campos en AuditEvents
    - **Propiedad 8: Completitud de campos en AuditEvents**
    - **Valida: Requisitos 6.2, 12.8, 2.8**

- [x] 9. Implementar Rate Limiter con Redis Token Bucket
  - [x] 9.1 Implementar `RedisRateLimiter` con script Lua atómico
    - Escribir `checkLimit()` con el script Lua `INCR + EXPIRE` para garantizar atomicidad en el conteo
    - Retornar `RateLimitResult(allowed, retryAfterSeconds)` con el tiempo restante hasta la próxima ventana
    - Integrar con API Gateway para rechazar solicitudes con HTTP 429 cuando se excede la cuota
    - _Requisitos: 8.1, 8.2, 8.3_

  - [ ]* 9.2 Escribir test de propiedad para rate limiting estricto
    - **Propiedad 6: Rate limiting estricto**
    - **Valida: Requisitos 8.4**

- [x] 10. Implementar Legacy Adapter Service y enrutamiento Strangler Fig
  - [x] 10.1 Implementar `LegacyAdapterService` con Feature Flags
    - Escribir `routeRequest()`: consultar `FeatureFlagService` (AWS AppConfig) para determinar si el módulo está migrado
    - Si migrado: reenviar al microservicio nuevo vía service discovery
    - Si no migrado: reenviar al monolito WebLogic manteniendo el mismo contrato de respuesta
    - _Requisitos: 7.1, 7.2_

  - [x] 10.2 Implementar Circuit Breaker con Resilience4j para el monolito legado
    - Configurar `CircuitBreaker` (Resilience4j) en `LegacyAdapterService`: abrir tras 5 fallos consecutivos, retornar HTTP 503 con mensaje descriptivo
    - Configurar timeout de 3 segundos para llamadas al monolito
    - _Requisitos: 7.3, 10.3_

  - [x] 10.3 Implementar Canary Deployment y rollback automático en API Gateway
    - Configurar enrutamiento por porcentaje en AWS API Gateway (pesos de tráfico configurables)
    - Implementar `CanaryMonitor`: verificar error rate del nuevo servicio cada 30s; si supera 1%, activar rollback enrutando 100% al Legacy Adapter
    - Garantizar exclusión mutua: ninguna solicitud procesada simultáneamente por ambos sistemas
    - _Requisitos: 1.6, 7.4, 7.5, 7.6, 7.7_

  - [ ]* 10.4 Escribir test de propiedad para enrutamiento Strangler Fig
    - **Propiedad 12: Enrutamiento Strangler Fig — exclusión mutua y completitud**
    - **Valida: Requisitos 7.1, 7.2, 7.4, 7.5, 1.2, 1.3**

- [ ] 11. Punto de control — Verificar que todos los tests pasan
  - Asegurarse de que todos los tests pasan. Consultar al usuario si surgen dudas.

- [-] 12. Implementar observabilidad y seguridad adicional
  - [x] 12.1 Configurar métricas Prometheus con Micrometer y trazas distribuidas
    - Añadir `MicrometerConfig` en cada microservicio para exponer métricas en formato Prometheus
    - Configurar `OpenTelemetry` / `Jaeger` para propagar `correlationId` como trace ID en todas las llamadas downstream
    - Centralizar logs en ELK Stack con el `correlationId` en cada entrada de log
    - _Requisitos: 11.1, 11.2, 11.3_

  - [x] 12.2 Configurar alertas en CloudWatch y dashboards Grafana
    - Definir alarmas CloudWatch para: CPU > 90%, error rate > 1%, latencia p99 > 2s
    - Crear dashboard Grafana unificado con métricas, trazas y logs
    - _Requisitos: 11.4, 11.5_

  - [x] 12.3 Implementar tokenización de datos de tarjetas y cifrado en reposo
    - Integrar AWS Payment Cryptography para tokenizar datos de tarjetas antes de cualquier persistencia
    - Verificar configuración de Oracle TDE, Redis encryption y S3 SSE-KMS en Terraform
    - _Requisitos: 12.5, 12.6_

  - [ ]* 12.4 Escribir test de propiedad para tokenización de datos de tarjetas
    - **Propiedad 20: Tokenización de datos de tarjetas**
    - **Valida: Requisitos 12.6**

- [x] 13. Implementar infraestructura como código y pipeline CI/CD
  - [x] 13.1 Crear módulos Terraform para infraestructura AWS
    - Definir módulos Terraform 1.6+ para: EKS, RDS Oracle Multi-AZ + réplica cross-region (us-west-2), ElastiCache Redis cluster, Amazon MSK, DocumentDB, ALB, API Gateway, CloudFront, Route 53 con health checks y failover, WAF con reglas OWASP Top 10
    - Configurar Cluster Autoscaler y HPA en EKS para los niveles de escala definidos (0–10K, 10K–100K, 100K–1M, 1M–5M usuarios)
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 10.1, 10.2, 10.5, 12.4, 13.1_

  - [x] 13.2 Crear Helm charts para cada microservicio
    - Crear chart Helm versionado por microservicio con: `Deployment`, `Service`, `HPA`, `KEDA ScaledObject` (para Transaction Service), `ConfigMap`, `ServiceAccount`
    - Configurar `livenessProbe` y `readinessProbe` para rollback automático si health check falla en los primeros 60s
    - _Requisitos: 13.2, 13.5_

  - [x] 13.3 Configurar pipeline CI/CD con tests de contrato
    - Definir pipeline (GitHub Actions / AWS CodePipeline) con etapas: build → unit tests → Spring Cloud Contract tests → push a ECR con escaneo de vulnerabilidades → deploy a staging → promote a producción
    - Habilitar escaneo de vulnerabilidades en ECR para todas las imágenes
    - _Requisitos: 13.3, 13.4_

- [x] 14. Integración final y cableado de todos los microservicios
  - [x] 14.1 Cablear API Gateway con todos los microservicios y Rate Limiter
    - Configurar rutas versionadas (`/v{n}/{recurso}`) en AWS API Gateway apuntando a cada microservicio
    - Integrar `RedisRateLimiter` en el filtro de entrada del API Gateway
    - Integrar validación JWT del Auth Service como pre-filtro obligatorio en todos los endpoints protegidos
    - Configurar throttling adicional por API key en AWS API Gateway (capa sobre Redis Rate Limiter)
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 8.5_

  - [x] 14.2 Cablear flujo completo de transacciones con Kafka, Notification y Audit
    - Verificar que `TransactionService` publica en `transactions.completed` y que `NotificationService` y `AuditService` consumen correctamente
    - Verificar que `AuditService` registra eventos de autenticación (`LOGIN_SUCCESS`, `LOGIN_FAILED`) y accesos a datos sensibles
    - Verificar retención de registros de auditoría configurada a 7 años en MongoDB
    - _Requisitos: 2.8, 4.4, 5.1, 6.5, 12.8_

- [ ] 15. Punto de control final — Verificar que todos los tests pasan
  - Asegurarse de que todos los tests pasan. Consultar al usuario si surgen dudas.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los tests de propiedad usan la librería **jqwik 1.8+**
- Los tests de integración usan **Testcontainers** con Oracle, Redis y Kafka reales
- Los tests de contrato entre microservicios usan **Spring Cloud Contract**
- El lenguaje de implementación es **Java 21** con **Spring Boot 3.2+**
