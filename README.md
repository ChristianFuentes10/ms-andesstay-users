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

### Flujo de Registro y Autenticación
1. **SignUp en Cognito**: El usuario se registra en Cognito (Hosted UI / SDK) y valida el código enviado a su email.
2. **Login**: Inicia sesión y obtiene su token JWT con claim `sub`.
3. **Alta de perfil (`POST /api/users`)**: Con el token en `Authorization: Bearer <token>`, el servicio crea el perfil en PostgreSQL vinculado a su `sub` con rol **`HUESPED`** forzado (evita escalada de privilegios).
4. **Consulta de perfil (`GET /api/users/me`)**: Retorna `200 OK` con los datos del perfil propio.

> Nota: Los endpoints de administración de tópicos (`/api/admin/kafka/*`) y reportería analítica (`/api/reportes/*`) están centralizados en `ms-andesstay-reservations`, que alimenta los tableros de Grafana.

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
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL | `jdbc:postgresql://andesstay-users-db:5432/andesstay_users` / `postgres` / `postgres` |
| `COGNITO_REGION` / `COGNITO_USER_POOL_ID` / `COGNITO_APP_CLIENT_ID` | Mismo User Pool que el resto | `us-east-1` / `us-east-1_l3aNhIuac` / `CHANGE_ME` |
| `COGNITO_ADMIN_ENABLED` | `false` por defecto (ideal en AWS Academy sin permisos IAM admin) | `false` |
| `USERS_BOOTSTRAP_ADMIN_EMAIL` | Crea el perfil del primer ADMIN (idempotente) | vacío |
| `DEFAULT_ROLE` | Rol para tokens sin grupos | `HUESPED` |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers Kafka compartidos | `kafka-1:29092,kafka-2:29093` (o `localhost:9092,localhost:9093` en host) |
| `KAFKA_TOPIC_USERS_EVENTS` | Tópico de eventos de usuario | `users.events` |
| `KAFKA_TOPIC_REPLICATION_FACTOR` | Factor de replicación en cluster | `2` |
| `FRONTEND_URL` / `CORS_ENABLED` | CORS (Angular) | `http://localhost:4200` / `true` |
| `SERVER_PORT` | Puerto | `8085` (el que espera el BFF: `USERS_SERVICE_URL`) |

---

## Ejecución local con Docker Desktop

```bash
docker compose up -d --build
```

Servicios desplegados:
- **`ms-andesstay-users`**: API Spring Boot en el puerto `8085` (Swagger: `http://localhost:8085/swagger-ui.html`, Health: `http://localhost:8085/actuator/health`).
- **`andesstay-users-db`**: PostgreSQL dedicado en el puerto `5435` del host para no chocar con catalog (`5432`) ni reservations (`5433`).
- **Red `andesstay-net`**: Conectado a la red externa compartida donde residen los brokers Kafka (`kafka-1:29092`, `kafka-2:29093`).

### Tests
```bash
mvn test
```
Tests unitarios y de integración de seguridad: `UserServiceTest`, `UserControllerSecurityTest`, `CognitoSecurityComponentsTest`, `UserRepositoryTest`.

---

## Integración con el resto del sistema
- **BFF**: Espera este servicio en `USERS_SERVICE_URL=http://localhost:8085` bajo el endpoint `/api/users`.
- **Apache Kafka**: Publica eventos de dominio (`USER_REGISTERED`, `USER_UPDATED`, `USER_ROLE_CHANGED`, `USER_DEACTIVATED`) en el tópico exclusivo **`users.events`** con particionado por ID de usuario. **No publica en `reporteria-eventos`** para evitar colisión de esquemas con `reservations` y su cola de mensajes muertos (DLT).
