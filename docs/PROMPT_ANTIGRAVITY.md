# Prompt para Antigravity — ms-andesstay-users

> Pegar completo en Antigravity con la carpeta `ms-andesstay-users` abierta en el workspace.
> Sirve para dos casos: **(A)** revisar/compilar/corregir el proyecto ya generado, o **(B)** regenerarlo desde cero si falta algo.

---

## ROL
Eres un ingeniero backend senior Java/Spring Boot. Trabajas en el proyecto **AndesStay** (Duoc UC, DSY1107 Cloud Native). Debes dejar `ms-andesstay-users` **compilando, con todos los tests en verde y listo para `docker compose up`**, sin cambiar su arquitectura ni su contrato HTTP.

## CONTEXTO
AndesStay es un sistema de reservas de alojamiento con microservicios: `ms-andesstay-bff` (:8080), `catalog` (:8081), `reservations` (:8082), `audit` (:8083), `notify` y este `users` (:8085). El frontend Angular inicia sesión con **AWS Cognito Hosted UI (PKCE)** y llama al BFF con el *access token*. Todos los microservicios son **OAuth2 Resource Servers** que validan el JWT de Cognito.

## STACK Y CONVENCIONES (obligatorias, iguales a catalog/reservations)
- Java 17, Spring Boot **3.3.4**, Maven, paquete base `cl.duoc.andesstay.users`.
- Capas: `controller → service → repository`, más `model`, `dto`, `exception`, `security`, `config`, `idp`.
- Propiedades en `application.properties` con variables de entorno y defaults; `application-prod.properties` con `ddl-auto=none` y `schema.sql`.
- Errores JSON con el record `ErrorResponse(timestamp, status, error, message, path)` vía `@RestControllerAdvice`. Nunca exponer stack traces.
- Seguridad: `JwtDecoder` con 3 validadores (issuer, `client_id`==`cognito.app-client-id`, `token_use`=`access`); roles desde `cognito:groups` → `ROLE_*`; sin grupos ⇒ rol `HUESPED` (`security.default-role`). `401/403` en JSON. CSRF off, sesión stateless.
- `TraceIdFilter` (`X-Trace-Id` + MDC), Actuator (`health` público, `prometheus`), springdoc en `/api-docs`.
- Docker multi-stage (Temurin 17, usuario no-root, HEALTHCHECK), `docker-compose.yml` con PostgreSQL 16 (host 5435), red `andesstay-net`.
- Tests: JUnit 5 + Mockito, `@WebMvcTest` + `spring-security-test` (`jwt()`), H2 en `src/test/resources/application.properties`.

## DOMINIO Y CONTRATO (NO CAMBIAR — el BFF ya depende de esto)
Roles (enum `Role`, igual al nombre del grupo de Cognito): `HUESPED`, `RECEPCIONISTA`, `ADMIN`, `AUDITOR`.
Entidad `User` (tabla `users`): `id` UUID, `cognitoSub` único nullable, `email` único (case-insensitive), `nombre`, `apellido`, `telefono`, `estado` (`ACTIVO|INACTIVO`), `roles` (`user_roles`), `createdAt`, `updatedAt`. **Nunca guardar contraseñas**: las administra Cognito.

| Método | Ruta | Acceso | Comportamiento |
|---|---|---|---|
| POST | `/api/users` | autenticado | Auto-registro: liga al `sub`, **rol HUESPED forzado** (ignora `roles`/`password`). Si el llamador es ADMIN: alta de otro usuario con `roles` (default HUESPED) y `password` temporal. 201. 409 si email/sub duplicado |
| GET | `/api/users/me` | autenticado | Perfil propio; 404 si no existe. Si un ADMIN lo pre-creó sin `sub`, lo vincula por email verificado en Cognito |
| GET | `/api/users` | ADMIN | Lista (`List`, no `Page`) con filtros `rol`, `estado`, `q`, `page`(0), `size`(50, máx 200); total en header `X-Total-Count` |
| GET | `/api/users/{id}` | ADMIN o dueño | `id` UUID |
| PUT | `/api/users/{id}` | ADMIN o dueño | Actualiza nombre/apellido/teléfono; `roles`/`estado` solo ADMIN (si no, 403) |
| DELETE | `/api/users/{id}` | ADMIN | Baja **lógica** (INACTIVO) + deshabilita en Cognito. 204. Idempotente |

Reglas de negocio: email normalizado a minúsculas; nunca dejar el sistema sin un ADMIN activo; un ADMIN no puede quitarse el rol, desactivarse ni eliminarse a sí mismo (409); si falla el guardado local tras crear la identidad en Cognito, compensar eliminándola.
Integración Cognito detrás de la interfaz `IdentityProviderClient`: `CognitoIdentityProviderClientAdapter` (`cognito.admin.enabled=true`, AWS SDK v2) y `NoOpIdentityProviderClient` (por defecto).

## TAREAS
1. **Revisa** todo el código bajo `src/` y los archivos de raíz (`pom.xml`, `Dockerfile`, `docker-compose.yml`, `.env.example`, `README.md`).
2. Ejecuta `mvn -B clean verify`. **Corrige** cualquier error de compilación, de configuración de Spring (beans, `@PreAuthorize`, SpEL `@userService`) o de tests. El código se escribió sin poder compilarlo, así que espera algún ajuste menor.
3. Verifica específicamente:
   - Que `UserRepository`/`UserSpecifications` funcionen en **PostgreSQL** y H2 (distinct + paginación + orden `createdAt`).
   - Que `GET /api/users/me` no choque con `GET /api/users/{id}` y que un UUID inválido responda 400.
   - Que los mensajes de `ErrorResponse` mantengan el formato y que ningún camino devuelva 500 por errores del cliente.
   - Que `schema.sql` coincida con la entidad JPA.
4. Levanta con `docker compose up -d --build` y prueba con `curl` (usa un JWT de prueba o el perfil de test): `/actuator/health` → 200; `/api/users/me` sin token → 401.
5. Si falta algo del contrato anterior, impleméntalo **siguiendo exactamente el estilo de `ms-andesstay-catalog` y `ms-andesstay-reservations`**.
6. Añade tests faltantes si encuentras casos sin cubrir (mínimo: cada regla de negocio y cada código de estado de la tabla).

## RESTRICCIONES
- No cambies rutas, nombres de campos JSON, códigos de estado ni el puerto 8085.
- No agregues dependencias fuera de Spring Boot 3.3.4, springdoc 2.6.0 y el AWS SDK v2 ya declarado.
- No guardes ni registres contraseñas, tokens ni emails en logs de nivel INFO o superior.
- No modifiques otros microservicios. Si ves una incompatibilidad con el BFF, repórtala, no la "arregles" allá.

## CRITERIOS DE ACEPTACIÓN
- [ ] `mvn clean verify` en verde (todos los tests).
- [ ] `docker compose up` arranca y `/actuator/health` responde `UP`.
- [ ] Matriz de acceso cumplida: sin token 401 · `/me` cualquier autenticado · listado solo ADMIN · `/{id}` ADMIN o dueño · DELETE solo ADMIN.
- [ ] Un auto-registro con `"roles":["ADMIN"]` termina con rol HUESPED.
- [ ] El último ADMIN activo no puede ser degradado, desactivado ni eliminado.
- [ ] Resumen final: lista de archivos cambiados y por qué.

## ENTREGA
Responde con: (1) resultado de `mvn clean verify`, (2) cambios realizados, (3) riesgos o decisiones pendientes.
