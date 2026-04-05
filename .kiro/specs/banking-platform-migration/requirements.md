# Documento de Requisitos: Migración de Plataforma Bancaria a Microservicios

## Introducción

Este documento define los requisitos funcionales y no funcionales para la migración completa de la plataforma bancaria desde un monolito Spring MVC / Struts (Java 8, WebLogic, DB2) hacia una arquitectura de microservicios moderna sobre AWS. La migración sigue el patrón Strangler Fig, reemplazando gradualmente módulos del monolito con nuevos microservicios, manteniendo continuidad operativa en todo momento. La plataforma debe soportar escalabilidad de 0 a 5 millones de usuarios con alta disponibilidad, resiliencia y observabilidad de nivel enterprise.

---

## Glosario

- **API_Gateway**: Componente de entrada único (AWS API Gateway + Kong) que gestiona enrutamiento, autenticación y throttling de todas las solicitudes externas.
- **Auth_Service**: Microservicio responsable de autenticación OAuth2/JWT y gestión de sesiones.
- **Account_Service**: Microservicio responsable del ciclo de vida de cuentas bancarias.
- **Transaction_Service**: Microservicio responsable del procesamiento de transacciones financieras con garantías ACID.
- **Notification_Service**: Microservicio responsable del envío asíncrono de notificaciones (email, SMS, push).
- **Audit_Service**: Microservicio responsable del registro inmutable de operaciones para cumplimiento regulatorio.
- **Legacy_Adapter**: Microservicio proxy que traduce contratos REST modernos a llamadas del monolito legado.
- **Rate_Limiter**: Componente que controla la tasa de solicitudes por cliente usando Redis Token Bucket.
- **Shard_Router**: Componente que determina el shard de Oracle DB destino para una operación dada.
- **Outbox_Processor**: Proceso periódico que reintenta publicar eventos Kafka pendientes desde la tabla OUTBOX.
- **Feature_Flag_Service**: Servicio que gestiona los flags de migración por módulo (AWS AppConfig).
- **Sistema**: La plataforma bancaria completa incluyendo todos los microservicios y su infraestructura.
- **Monolito**: El sistema legado Spring MVC / Struts desplegado en WebLogic con base de datos DB2.
- **JWT**: JSON Web Token usado como mecanismo de autenticación stateless.
- **idempotencyKey**: Clave única por solicitud de transacción que garantiza procesamiento exactamente una vez.
- **Virtual_Thread**: Hilo virtual de Java 21 (Project Loom) para concurrencia masiva con bajo overhead.

---

## Requisitos

### Requisito 1: API Gateway y Enrutamiento

**Historia de usuario:** Como desarrollador externo, quiero un punto de entrada único y consistente para todas las APIs bancarias, para que pueda integrar mis aplicaciones sin conocer la topología interna del sistema.

#### Criterios de Aceptación

1. THE API_Gateway SHALL exponer todos los endpoints bajo rutas versionadas con el formato `/v{n}/{recurso}`.
2. WHEN una solicitud entrante incluye una ruta correspondiente a un módulo migrado, THE API_Gateway SHALL enrutar la solicitud al microservicio correspondiente.
3. WHEN una solicitud entrante incluye una ruta correspondiente a un módulo no migrado, THE API_Gateway SHALL enrutar la solicitud al Legacy_Adapter.
4. WHEN el Rate_Limiter determina que un cliente ha excedido su cuota, THE API_Gateway SHALL rechazar la solicitud con HTTP 429 Too Many Requests.
5. WHEN una solicitud no incluye credenciales de autenticación válidas, THE API_Gateway SHALL rechazar la solicitud con HTTP 401 Unauthorized antes de enrutarla a cualquier microservicio.
6. THE API_Gateway SHALL soportar Canary Deployments enrutando un porcentaje configurable del tráfico al nuevo servicio durante la migración Strangler Fig.
7. WHEN se recibe una solicitud, THE API_Gateway SHALL propagar un `correlationId` único en los headers hacia todos los microservicios downstream.

---

### Requisito 2: Autenticación y Autorización

**Historia de usuario:** Como desarrollador externo, quiero autenticarme con OAuth2/JWT para que mis solicitudes a la API bancaria sean seguras y mis sesiones gestionadas correctamente.

#### Criterios de Aceptación

1. WHEN un cliente envía credenciales válidas, THE Auth_Service SHALL retornar un `accessToken` JWT con TTL de 15 minutos y un `refreshToken` con TTL de 7 días.
2. WHEN un cliente envía credenciales inválidas, THE Auth_Service SHALL incrementar el contador de intentos fallidos en Redis y retornar HTTP 401.
3. WHEN un cliente acumula 5 intentos de autenticación fallidos consecutivos, THE Auth_Service SHALL bloquear la cuenta temporalmente durante 15 minutos y retornar HTTP 423 Locked.
4. WHEN un cliente presenta un `refreshToken` válido, THE Auth_Service SHALL emitir un nuevo `accessToken` sin requerir re-autenticación con credenciales.
5. WHEN un JWT es validado, THE Auth_Service SHALL verificar primero en la caché Redis antes de realizar cualquier operación adicional.
6. WHEN un JWT es revocado, THE Auth_Service SHALL eliminar la entrada correspondiente de la caché Redis de forma inmediata.
7. WHEN un JWT expirado es presentado a cualquier endpoint protegido, THE Sistema SHALL retornar HTTP 401 con header `WWW-Authenticate: Bearer error="token_expired"`.
8. THE Auth_Service SHALL registrar un AuditEvent de tipo `LOGIN_SUCCESS` o `LOGIN_FAILED` por cada intento de autenticación.

---

### Requisito 3: Gestión de Cuentas Bancarias

**Historia de usuario:** Como cliente bancario, quiero gestionar mis cuentas (crear, consultar, actualizar, cerrar) para que pueda administrar mis finanzas a través de la plataforma.

#### Criterios de Aceptación

1. WHEN se recibe una solicitud de creación de cuenta con datos válidos, THE Account_Service SHALL crear la cuenta en Oracle DB y retornar el objeto `Account` con su `accountId` generado.
2. WHEN se solicita una cuenta por `accountId`, THE Account_Service SHALL consultar primero la caché Redis antes de acceder a Oracle DB.
3. WHEN se solicita el saldo de una cuenta, THE Account_Service SHALL retornar el saldo desde la caché Redis con TTL de 30 segundos, o desde Oracle DB si la caché está vacía.
4. WHEN se actualiza el saldo de una cuenta, THE Account_Service SHALL invalidar la entrada correspondiente en la caché Redis de forma inmediata.
5. IF el `balance` de una cuenta CHECKING o SAVINGS es negativo, THEN THE Account_Service SHALL rechazar la operación con HTTP 422 Unprocessable Entity.
6. IF el código `currency` no es un código ISO 4217 válido, THEN THE Account_Service SHALL rechazar la creación de la cuenta con HTTP 400 Bad Request.
7. THE Account_Service SHALL garantizar que el `accountId` es inmutable tras la creación de la cuenta.
8. WHEN se cierran múltiples cuentas de un mismo cliente, THE Account_Service SHALL procesar cada cierre de forma independiente sin afectar las demás cuentas.

---

### Requisito 4: Procesamiento de Transacciones Financieras

**Historia de usuario:** Como cliente bancario, quiero realizar transferencias y pagos con garantías de integridad, para que mis fondos sean movidos correctamente y sin duplicaciones.

#### Criterios de Aceptación

1. WHEN se recibe una solicitud de transacción con `idempotencyKey` ya procesado, THE Transaction_Service SHALL retornar el resultado original sin re-procesar la transacción.
2. WHEN se procesa una transacción exitosa con monto A entre cuenta origen S y cuenta destino D, THE Transaction_Service SHALL garantizar que `balance(S)_post = balance(S)_pre - A` y `balance(D)_post = balance(D)_pre + A`.
3. WHEN el saldo de la cuenta origen es insuficiente para cubrir el monto de la transacción, THE Transaction_Service SHALL rechazar la operación con HTTP 422 y código `INSUFFICIENT_FUNDS` sin modificar ningún saldo.
4. WHEN una transacción es completada exitosamente, THE Transaction_Service SHALL publicar un evento `TransactionCompleted` en el topic Kafka `transactions.completed` de forma asíncrona.
5. WHEN Kafka no está disponible al momento de publicar un evento, THE Transaction_Service SHALL almacenar el evento en la tabla `OUTBOX` de Oracle DB para reintento posterior.
6. WHEN una transacción es completada, THE Transaction_Service SHALL invalidar las entradas de caché Redis de saldo para las cuentas origen y destino.
7. WHEN se solicita la reversión de una transacción completada, THE Transaction_Service SHALL crear una transacción de tipo `REVERSAL` y restaurar los saldos originales de forma atómica.
8. THE Transaction_Service SHALL utilizar Virtual_Threads de Java 21 para el procesamiento concurrente de transacciones.
9. THE Transaction_Service SHALL adquirir un lock distribuido en Redis sobre la cuenta origen antes de modificar cualquier saldo.
10. THE Shard_Router SHALL determinar el shard de Oracle DB de forma determinística usando `hash(sourceAccountId) % NUM_SHARDS`.

---

### Requisito 5: Notificaciones Asíncronas

**Historia de usuario:** Como cliente bancario, quiero recibir notificaciones de las operaciones realizadas en mis cuentas, para que esté informado de cualquier movimiento en tiempo real.

#### Criterios de Aceptación

1. WHEN el Notification_Service consume un evento de Kafka, THE Notification_Service SHALL enviar la notificación correspondiente por el canal apropiado (email, SMS o push).
2. WHEN una notificación es enviada, THE Notification_Service SHALL registrar el resultado en MongoDB con el estado final (`SENT`, `FAILED`).
3. IF el servicio externo de notificación (AWS SES/SNS) no está disponible, THEN THE Notification_Service SHALL reintentar el envío con backoff exponencial antes de marcar la notificación como `FAILED`.
4. THE Notification_Service SHALL operar de forma completamente asíncrona sin bloquear el path crítico de procesamiento de transacciones.

---

### Requisito 6: Auditoría e Inmutabilidad

**Historia de usuario:** Como oficial de cumplimiento regulatorio, quiero que todas las operaciones del sistema queden registradas de forma inmutable, para que pueda realizar auditorías y cumplir con los requisitos legales.

#### Criterios de Aceptación

1. WHEN el Audit_Service recibe un evento de Kafka, THE Audit_Service SHALL persistir el `AuditEvent` en MongoDB de forma append-only sin modificar registros existentes.
2. THE Audit_Service SHALL registrar en cada `AuditEvent` los campos: `eventId`, `correlationId`, `serviceOrigin`, `action`, `actorId`, `resourceId`, `payload`, `ipAddress` y `timestamp`.
3. THE Sistema SHALL garantizar que ningún `AuditEvent` puede ser modificado o eliminado tras su inserción en MongoDB.
4. WHEN se consultan eventos de auditoría, THE Audit_Service SHALL soportar filtrado por `correlationId`, `actorId`, `resourceId` y rango de `timestamp`.
5. THE Sistema SHALL retener los registros de auditoría durante un mínimo de 7 años.

---

### Requisito 7: Migración Strangler Fig y Legacy Adapter

**Historia de usuario:** Como arquitecto de sistemas, quiero migrar gradualmente los módulos del monolito sin interrumpir el servicio, para que la transición sea transparente para los clientes y desarrolladores externos.

#### Criterios de Aceptación

1. WHEN el Legacy_Adapter recibe una solicitud para un módulo marcado como migrado en el Feature_Flag_Service, THE Legacy_Adapter SHALL enrutar la solicitud al microservicio nuevo correspondiente.
2. WHEN el Legacy_Adapter recibe una solicitud para un módulo no migrado, THE Legacy_Adapter SHALL reenviar la solicitud al Monolito en WebLogic manteniendo el mismo contrato de respuesta.
3. WHEN el Monolito no responde dentro del timeout configurado, THE Legacy_Adapter SHALL activar el Circuit Breaker y retornar HTTP 503 con mensaje descriptivo.
4. THE Sistema SHALL garantizar que durante la migración ninguna solicitud es procesada simultáneamente por el Monolito y el nuevo microservicio.
5. THE Sistema SHALL garantizar que durante la migración ninguna solicitud se pierde; toda solicitud es procesada por exactamente uno de los dos sistemas.
6. WHERE el Canary Deployment está activo, THE API_Gateway SHALL enrutar el porcentaje configurado de tráfico al nuevo servicio y el resto al Legacy_Adapter.
7. WHEN el error rate del nuevo servicio supera el 1% durante un Canary Deployment, THE Sistema SHALL activar rollback automático enrutando el 100% del tráfico al Legacy_Adapter.

---

### Requisito 8: Rate Limiting

**Historia de usuario:** Como operador de la plataforma, quiero controlar la tasa de solicitudes por cliente, para que ningún cliente pueda saturar el sistema y afectar a otros usuarios.

#### Criterios de Aceptación

1. THE Rate_Limiter SHALL implementar el algoritmo Token Bucket usando scripts Lua en Redis para garantizar atomicidad en el conteo de solicitudes.
2. WHEN un cliente realiza solicitudes dentro de su cuota, THE Rate_Limiter SHALL permitir la solicitud e incrementar el contador en Redis de forma atómica.
3. WHEN un cliente excede su cuota en la ventana de tiempo activa, THE Rate_Limiter SHALL rechazar la solicitud y retornar el tiempo restante hasta la próxima ventana en el campo `retryAfterSeconds`.
4. THE Rate_Limiter SHALL garantizar que para todo cliente C en cualquier ventana W, el número de solicitudes permitidas nunca excede `maxRequests(C)`.
5. WHERE la configuración de rate limiting por API key está activa en AWS API Gateway, THE API_Gateway SHALL aplicar throttling adicional por encima del Rate_Limiter de Redis.

---

### Requisito 9: Escalabilidad y Autoscaling

**Historia de usuario:** Como arquitecto de infraestructura, quiero que la plataforma escale automáticamente desde 0 hasta 5 millones de usuarios, para que el rendimiento se mantenga bajo cualquier nivel de carga.

#### Criterios de Aceptación

1. WHEN el uso de CPU de un microservicio supera el 70%, THE Sistema SHALL escalar horizontalmente el número de pods mediante HPA (Horizontal Pod Autoscaler).
2. WHEN el lag de consumo de Kafka supera 1000 mensajes en el topic `transactions.pending`, THE Sistema SHALL escalar los pods del Transaction_Service mediante KEDA.
3. WHEN los pods disponibles son insuficientes para la carga actual, THE Sistema SHALL escalar los nodos del cluster EKS mediante el Cluster Autoscaler.
4. THE Sistema SHALL soportar los siguientes niveles de escala sin degradación de servicio:
   - 0–10K usuarios: mínimo 2 réplicas por servicio, 3 nodos EKS.
   - 10K–100K usuarios: 5–10 réplicas por servicio, 10 nodos EKS.
   - 100K–1M usuarios: 20+ nodos EKS con KEDA activo.
   - 1M–5M usuarios: configuración multi-región activa con réplicas de lectura Oracle y Redis en modo cluster.
5. THE Transaction_Service SHALL utilizar Virtual_Threads para soportar 100K+ conexiones concurrentes con footprint mínimo de memoria.

---

### Requisito 10: Alta Disponibilidad y Recuperación ante Desastres

**Historia de usuario:** Como director de operaciones, quiero que la plataforma tenga alta disponibilidad y capacidad de recuperación ante desastres, para que el servicio bancario no se interrumpa ante fallos de infraestructura.

#### Criterios de Aceptación

1. THE Sistema SHALL desplegar todos los microservicios en al menos 3 Availability Zones dentro de la región primaria (us-east-1).
2. THE Sistema SHALL mantener una réplica cross-region de Oracle DB en us-west-2 con RPO menor a 1 minuto.
3. WHEN Oracle DB primario no responde en menos de 3 segundos, THE Transaction_Service SHALL activar el Circuit Breaker (Resilience4j) tras 5 fallos consecutivos y retornar HTTP 503.
4. WHEN el Circuit Breaker está abierto para escrituras, THE Account_Service SHALL redirigir las consultas de lectura a las réplicas de Oracle DB.
5. THE Sistema SHALL implementar Route 53 con health checks y failover automático hacia la región DR (us-west-2) ante fallo de la región primaria.
6. WHEN el Outbox_Processor detecta eventos pendientes en la tabla OUTBOX, THE Outbox_Processor SHALL reintentar la publicación en Kafka hasta que sea exitosa.

---

### Requisito 11: Observabilidad

**Historia de usuario:** Como ingeniero de operaciones, quiero visibilidad completa del estado y rendimiento del sistema, para que pueda detectar y resolver incidentes rápidamente.

#### Criterios de Aceptación

1. THE Sistema SHALL exponer métricas en formato Prometheus desde cada microservicio mediante Micrometer.
2. THE Sistema SHALL propagar el `correlationId` como trace ID a través de todos los microservicios para trazabilidad distribuida con Jaeger / AWS X-Ray.
3. THE Sistema SHALL centralizar todos los logs de los microservicios en el ELK Stack con retención configurable.
4. WHEN una métrica de negocio supera un umbral crítico (CPU > 90%, error rate > 1%, latencia p99 > 2s), THE Sistema SHALL generar una alerta en CloudWatch.
5. THE Sistema SHALL visualizar métricas, trazas y logs de forma unificada en dashboards de Grafana.

---

### Requisito 12: Seguridad

**Historia de usuario:** Como oficial de seguridad, quiero que la plataforma cumpla con los estándares de seguridad bancaria y PCI DSS, para que los datos de los clientes y las transacciones estén protegidos.

#### Criterios de Aceptación

1. THE Sistema SHALL cifrar todas las comunicaciones entre microservicios dentro del cluster EKS usando mTLS mediante Istio service mesh.
2. THE Sistema SHALL cifrar todas las comunicaciones externas usando TLS 1.3.
3. THE Sistema SHALL almacenar todas las credenciales, API keys y certificados exclusivamente en AWS Secrets Manager, nunca en variables de entorno ni en código fuente.
4. THE Sistema SHALL aplicar reglas WAF de OWASP Top 10 (SQL injection, XSS, etc.) en AWS WAF antes de que cualquier solicitud llegue al API Gateway.
5. THE Sistema SHALL cifrar los datos en reposo en Oracle DB (TDE), Redis y S3 (SSE-KMS).
6. THE Sistema SHALL tokenizar los datos de tarjetas de pago usando AWS Payment Cryptography, sin almacenar datos de tarjetas en texto plano.
7. THE Auth_Service SHALL rotar las claves de firma JWT (RS256) cada 90 días sin interrumpir sesiones activas.
8. WHEN se accede a datos sensibles de un cliente, THE Audit_Service SHALL registrar el acceso en MongoDB con todos los campos requeridos por el Requisito 6.

---

### Requisito 13: Infraestructura como Código y CI/CD

**Historia de usuario:** Como ingeniero de DevOps, quiero que toda la infraestructura y los despliegues estén automatizados, para que los cambios sean reproducibles, auditables y seguros.

#### Criterios de Aceptación

1. THE Sistema SHALL definir toda la infraestructura AWS (EKS, RDS, MSK, ElastiCache, etc.) usando Terraform 1.6+.
2. THE Sistema SHALL empaquetar cada microservicio como un Helm chart versionado para su despliegue en Kubernetes.
3. THE Sistema SHALL almacenar todas las imágenes Docker en Amazon ECR con escaneo de vulnerabilidades habilitado.
4. WHEN se despliega una nueva versión de un microservicio, THE Sistema SHALL ejecutar los tests de contrato (Spring Cloud Contract) antes de promover la imagen a producción.
5. THE Sistema SHALL soportar rollback automático de un despliegue si el health check del nuevo pod falla dentro de los primeros 60 segundos.
