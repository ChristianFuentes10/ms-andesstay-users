# ms-andesstay-users

Microservicio de dominio del caso **AndesStay** (DSY1107 - Desarrollo Cloud Native I, Duoc UC).

Responsable del dominio **Usuarios**: registro, perfil, **roles** y baja de usuarios. Las credenciales las administra **AWS Cognito**; este servicio guarda el perfil de negocio y mantiene los roles sincronizados con los **grupos de Cognito**.

---

## Stack

- **Java 17** · **Spring Boot 3.3.4** (Web, Data JPA, Validation, Security, Actuator)
- **Spring Security OAuth2 Resource Server** (JWT - AWS Cognito)
- **AWS SDK v2** (`cognitoidentityprovider`) para altas y sincronización de grupos (opcional)
- **PostgreSQL 16** · **Docker Compose** · **Maven 3.9+** · **springdoc-openapi** (Swagger)

Mismas convenciones que `ms-andesstay-catalog` / `ms-andesstay-reservations`: paquete `cl.duoc.andesstay.users`, capas `controller → service → repository`, DTOs de salida, `ErrorResponse` común, `TraceIdFilter` (`X-Trace-Id`), misma validación de tokens (issuer + `client_id` + `token_use=access`).

---

## Arquitectura

```
Angular ──(Cognito Hosted UI, PKCE)──► token
   │
   ▼
 BFF :8080  ──(Bearer propagado)──►  ms-andesstay-users :8085 ──► PostgreSQL (andesstay_users)
                                              │
                                              └─(opcional, AWS SDK)──► Cognito User Pool (usuarios + grupos)
```

Capas: `config/` · `controller/` · `service/` · `repository/` · `model/` · `dto/` · `exception/` · `security/` · `idp/` (puerto `IdentityProviderClient` con implementación Cognito y no-op).

### Roles

| Rol | Descripción |
|-----|-------------|
| `HUESPED` | Cliente. Rol por defecto de todo auto-registro |
| `RECEPCIONISTA` | Gestiona reservas |
| `ADMIN` | Administra usuarios, roles, catálogo y reportes |
| `AUDITOR` | Consulta auditoría |

El nombre del rol **es** el nombre del grupo de Cognito (`cognito:groups` → `ROLE_<NOMBRE>`), por lo que BFF, catalog, reservations y audit los entienden sin cambios.

---

## API (`/api/users`)

| Método | Ruta | Acceso | Descripción |
|--------|------|--------|-------------|
| `POST` | `/api/users` | Autenticado | Registro. **Auto-registro** (no ADMIN): perfil ligado al `sub` del token, rol `HUESPED` forzado. **Alta por ADMIN**: puede indicar `roles` y `password` temporal |
| `GET` | `/api/users/me` | Autenticado | Perfil propio (404 si aún no se registró) |
| `GET` | `/api/users` | ADMIN | Lista. Filtros: `rol`, `estado`, `q`, `page`, `size`. Total en header `X-Total-Count` |
| `GET` | `/api/users/{id}` | ADMIN o dueño | Detalle (UUID) |
| `PUT` | `/api/users/{id}` | ADMIN o dueño | Actualiza perfil. `roles` y `estado` solo los aplica un ADMIN (si no, 403) |
| `DELETE` | `/api/users/{id}` | ADMIN | **Baja lógica** (`INACTIVO`) y deshabilita login en Cognito. 204 |

### API Kafka Admin (`/api/admin/kafka/topics`) - *Requerimiento Rúbrica Entrega 3*
| Método | Ruta | Acceso | Descripción |
|--------|------|--------|-------------|
| `GET` | `/api/admin/kafka/topics` | ADMIN | Lista todos los tópicos y número de particiones |
| `GET` | `/api/admin/kafka/topics/{name}` | ADMIN | Detalle del tópico, particiones, réplicas y configuraciones activas |
| `POST` | `/api/admin/kafka/topics` | ADMIN | **Creación** de tópico (nombre, particiones, réplicas, configs) |
| `PUT` | `/api/admin/kafka/topics/{name}` | ADMIN | **Modificación** de tópico (aumento de particiones y actualización de configs) |
| `DELETE` | `/api/admin/kafka/topics/{name}` | ADMIN | **Eliminación** de tópico en Kafka |

### API Reportería y KPI en tiempo real (`/api/reporteria`) - *Requerimiento Rúbrica Entrega 3*
| Método | Ruta | Acceso | Descripción |
|--------|------|--------|-------------|
| `GET` | `/api/reporteria/kpi` | ADMIN | KPIs consolidados: total usuarios, activos/inactivos, por rol y métricas de eventos Kafka |
| `GET` | `/api/reporteria/eventos` | ADMIN | Traza de auditoría de los últimos eventos procesados por el consumidor Kafka |

Registro (cuerpo):
```json
{ "nombre": "Juan", "apellido": "Pérez", "email": "juan@test.com", "telefono": "+56 9 1234 5678", "password": "12345678", "roles": ["RECEPCIONISTA"] }
```

### Reglas de negocio
- Un auto-registro nunca puede elegir roles (evita escalada de privilegios).
- Email único (sin distinguir mayúsculas) y un perfil por `sub`.
- Con Cognito habilitado, el email de un auto-registro debe coincidir con el verificado en Cognito.
- Nunca se deja el sistema sin un ADMIN activo; un ADMIN no puede quitarse el rol, desactivarse ni eliminarse a sí mismo (409).
- Un perfil pre-creado por un ADMIN se vincula al `sub` en el primer `GET /api/users/me`.

### Errores
Mismo formato que reservations/BFF: `{ "timestamp", "status", "error", "message", "path" }`.
`400` validación · `401` sin token · `403` sin permiso · `404` no existe · `409` duplicado/regla de negocio · `502` falla Cognito.

---

## Variables de entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL | `jdbc:postgresql://localhost:5432/andesstay_users` / `postgres` / `postgres` |
| `COGNITO_REGION` / `COGNITO_USER_POOL_ID` / `COGNITO_APP_CLIENT_ID` | Mismo User Pool que el resto | `us-east-1` / `CHANGE_ME` / `CHANGE_ME` |
| `COGNITO_ADMIN_ENABLED` | `true` = crea usuarios y sincroniza grupos en Cognito | `false` |
| `USERS_BOOTSTRAP_ADMIN_EMAIL` | Crea el perfil del primer ADMIN (idempotente) | vacío |
| `DEFAULT_ROLE` | Rol para tokens sin grupos | `HUESPED` |
| `KAFKA_BOOTSTRAP_SERVERS` | Servidor Kafka | `localhost:9092` (en docker `kafka:29092`) |
| `KAFKA_TOPIC_REPORTERIA` | Tópico para eventos de reportería | `reporteria-eventos` |
| `FRONTEND_URL` / `CORS_ENABLED` | CORS (Angular) | `http://localhost:4200` / `true` |
| `SERVER_PORT` | Puerto | `8085` (el que espera el BFF: `USERS_SERVICE_URL`) |

---

## Ejecución local con Docker Desktop (Entrega 3 Completa)

```bash
docker compose up -d --build
```

Servicios desplegados y puertos expuestos:
- **API Usuarios & Kafka Admin**: `http://localhost:8085` (Swagger: `/swagger-ui.html`)
- **PostgreSQL**: `localhost:5435`
- **Apache Kafka Broker**: `localhost:9092`
- **Zookeeper**: `localhost:2181`
- **Kafka-UI (Consola Web de Kafka)**: `http://localhost:8086` (ver y administrar tópicos, mensajes y brokers)
- **Prometheus**: `http://localhost:9090` (recolecta `/actuator/prometheus`)
- **Grafana**: `http://localhost:3000` (permite embeber dashboards con iframe en Angular)

### Tests
```bash
mvn test
```
Tests unitarios y de integración de seguridad: `UserServiceTest`, `UserControllerSecurityTest`, `KafkaAdminControllerTest`, `ReporteriaControllerTest`, `CognitoSecurityComponentsTest`, `UserRepositoryTest`.

---

## Integración con el resto del sistema
- **BFF**: ya tiene `UsersClient`/`UsersProxyController` y espera este servicio en `USERS_SERVICE_URL=http://localhost:8085`.
- **Apache Kafka**: Publica eventos (`USER_REGISTERED`, `USER_UPDATED`, `USER_ROLE_CHANGED`, `USER_DEACTIVATED`) en el tópico `reporteria-eventos` con la clave del usuario (partición por ID) y el consumidor actualiza las tablas de reportería en PostgreSQL para alimentar los tableros analíticos.
