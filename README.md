# API REST de Clínica Veterinaria — Arquitectura de Microservicios

API en microservicios para la gestión de una clínica veterinaria: usuarios con roles
(`ADMIN`, `VET`, `CLIENTE`), mascotas, citas médicas con reglas de negocio y expedientes
clínicos. Spring Boot 3.5.16 · Java 21 · MySQL 8 · JWT + BCrypt · Swagger · pruebas JUnit 5 · k6.
Ejecución local (sin Docker).

---

## 1. Arquitectura

```
                          ┌────────────────────┐
   cliente ──HTTP───────▶ │  api-gateway :8090 │  (Spring Cloud Gateway, CORS, enrutado)
                          └─────────┬──────────┘
        ┌──────────┬──────────┬─────┴──────┬─────────────┐
        ▼          ▼          ▼            ▼             ▼
 ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐
 │auth-service│ │  user-   │ │ mascota- │ │  cita-   │ │ expediente-    │
 │   :8081    │ │service   │ │ service  │ │ service  │ │ service        │
 │  (sin BD)  │ │  :8082   │ │  :8083   │ │  :8084   │ │    :8085       │
 └─────┬──────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬─────────┘
       │  interno    │            │            │              │
       │  X-Internal-│─Key───────▶│            │              │
       └────────────▶└────────────┴────────────┴──────────────┘
                              ▼
                      ┌──────────────┐
                      │  MySQL 8     │  vet_usuarios_in5am / vet_mascotas_in5am /
                      │    :3306     │  vet_citas_in5am / vet_expedientes_in5am
                      └──────────────┘
```

| Módulo | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `api-gateway` | **8090** | — | Punto único de entrada, CORS, enrutado |
| `auth-service` | 8081 | — | Registro, login, emisión/validación de JWT |
| `user-service` | 8082 | `vet_usuarios_in5am` | CRUD de usuarios (dueño de la tabla `usuarios`, BCrypt) |
| `mascota-service` | 8083 | `vet_mascotas_in5am` | CRUD de mascotas |
| `cita-service` | 8084 | `vet_citas_in5am` | Citas médicas y reglas de negocio |
| `expediente-service` | 8085 | `vet_expedientes_in5am` | Expedientes clínicos |

> El gateway usa el **8090** porque el 8080 está ocupado en la máquina de desarrollo.

Cada servicio valida el JWT localmente (HS256, misma `JWT_SECRET`) con filtros
`STATELESS`; no hay sesión ni dependencia del gateway para la autorización.
Los endpoints `/internal/**` se protegen con el header `X-Internal-Key`
(`INTERNAL_SERVICE_KEY`) y rol `ROLE_SERVICE`; el gateway **no** enruta `/internal/**`.

---

## 2. Endpoints principales

Todos bajo el gateway `http://localhost:8090` (Swagger directo en el puerto de cada servicio).
Los cuerpos JSON se atienden en **UTF-8**.

### auth-service
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/registro` | pública | Registra siempre como `CLIENTE` (el campo `rol` del body se ignora) → `201 {token,type,expiresIn}` |
| POST | `/api/v1/auth/login` | pública | `200 {token,type,expiresIn}` / `401` credenciales inválidas |
| GET | `/api/v1/auth/me` | JWT | Datos del usuario del token |

### user-service
| Método | Ruta | Rol |
|---|---|---|
| GET/POST | `/api/v1/usuarios` | `ADMIN` (paginado) |
| GET | `/api/v1/usuarios/veterinarios` | cualquier autenticado |
| GET/PUT/DELETE | `/api/v1/usuarios/{id}` | `ADMIN` (no puede eliminarse a sí mismo → `409`) |

### mascota-service
| Método | Ruta | Rol |
|---|---|---|
| POST | `/api/v1/mascotas` | `CLIENTE`, `ADMIN` (el dueño es el token; `clienteId` opcional solo para `ADMIN`) |
| GET | `/api/v1/mascotas/mis-mascotas` | `CLIENTE` |
| GET | `/api/v1/mascotas` | `ADMIN` (filtros `clienteId`, `especie`) |
| GET | `/api/v1/mascotas/{id}` | `VET`, `ADMIN` |

### cita-service
| Método | Ruta | Rol |
|---|---|---|
| POST | `/api/v1/citas` | `CLIENTE`, `ADMIN` |
| GET | `/api/v1/citas/agenda?fecha&veterinarioId` | `VET` (la suya), `ADMIN` |
| PATCH | `/api/v1/citas/{id}/cancelar` | `CLIENTE` (solo la suya), `ADMIN` |
| GET | `/api/v1/citas/{id}` | autenticado (dueño, vet de la cita o `ADMIN`) |

### expediente-service
| Método | Ruta | Rol |
|---|---|---|
| POST | `/api/v1/expedientes` | `VET` (solo sus citas, cita `PENDIENTE`), `ADMIN` |
| GET | `/api/v1/expedientes/mascotas/{mascotaId}` | autenticado (dueño de la mascota, `VET`, `ADMIN`) |

**Reglas de negocio de citas**

1. Duración fija de 30 minutos; solape = fecha dentro del rango estricto
   `(nueva−30min, nueva+30min)` del veterinario, descartando citas `CANCELADA` → `409`.
2. Máximo **2 citas pendientes** por cliente y día calendario → `409`.
3. No se crean citas en el pasado → `422`.
4. El usuario indicado debe tener rol `VET` → `422`.
5. Cancelación solo si `ahora < fechaHora − 2 h` y estado `PENDIENTE` → `409`/`422`.
6. Concurrencia: candado por fila (`bloqueo_recurso` + `SELECT … FOR UPDATE`, orden fijo
   `VET:` → `CLIENTE:`) y `UNIQUE(veterinario_id, fecha_hora)` como red de seguridad.
7. Expediente: uno por cita; al guardarlo se completa la cita
   (`POST /internal/citas/{id}/completar`, transición CAS `PENDIENTE → COMPLETADA`).

**Formato de error único** en todos los servicios:

```json
{ "timestamp": "2026-10-06T10:00:00", "status": 409, "error": "CONFLICT",
  "message": "El veterinario ya tiene una cita en ese horario",
  "path": "/api/v1/citas" }
```
`400` validación (con `details[]`), `401` JWT/credenciales, `403` rol/propiedad,
`404` no existe, `405` método no soportado, `409` conflicto, `415` media type no soportado,
`422` regla de negocio, `503` dependencia caída, `500` sin stack trace.

---

## 3. Ejecución (local, sin Docker)

### 3.1 Requisitos

- **JDK 21** (`java -version`).
- **MySQL 8** local en `localhost:3306`. No hace falta crear los esquemas a mano: se crean
  solos al arrancar cada servicio (`createDatabaseIfNotExist=true`).

Incluye el **Maven Wrapper**, así que **no** es necesario instalar Maven.

### 3.2 Puesta en marcha

Los valores por defecto apuntan a MySQL local (`root` / `GAMSTERfredy1`) y a claves
JWT/internas de desarrollo, por lo que **no hay que configurar variables** para probar en
la máquina local.

```bash
# Windows (PowerShell o CMD)
mvnw.cmd -B clean install -DskipTests

# Linux / macOS / Git Bash
./mvnw -B clean install -DskipTests
./run-local.sh          # levanta los 6 servicios y espera a que estén listos
```

Si se prefiere arrancar los servicios a mano (Windows), un proceso por cada uno:

```powershell
java -jar user-service\target\user-service-1.0.0-SNAPSHOT.jar
java -jar auth-service\target\auth-service-1.0.0-SNAPSHOT.jar
java -jar mascota-service\target\mascota-service-1.0.0-SNAPSHOT.jar
java -jar cita-service\target\cita-service-1.0.0-SNAPSHOT.jar
java -jar expediente-service\target\expediente-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar
```

- `createDatabaseIfNotExist=true` crea los cuatro esquemas en el primer arranque.
- `user-service` siembra los usuarios iniciales con `data.sql` (idempotente).
- Swagger: `http://localhost:8081/swagger-ui.html` … `:8085` y
  `http://localhost:8090/actuator/health` para el gateway.
- Para cambiar credenciales de MySQL, el `JWT_SECRET`, etc., se puede copiar
  `.env.example` a `.env` y editarlo; `run-local.sh` lo carga si existe. Sin `.env`
  rigen los valores por defecto de desarrollo.

### 3.3 Usuarios semilla

| Email | Contraseña | Rol |
|---|---|---|
| `admin@veterinaria.com` | `Admin123!` | `ADMIN` |
| `veterinario@veterinaria.com` | `Vet12345!` | `VET` |
| `cliente@veterinaria.com` | `Cliente123!` | `CLIENTE` |

---

## 4. Configuración (valores de desarrollo por defecto)

Todos los parámetros se pueden sobreescribir por variables de entorno (`.env` está en
`.gitignore`; usa `.env.example`). Los defaults permiten arrancar sin configurar nada en
local:

| Variable | Uso | Default |
|---|---|---|
| `JWT_SECRET` | Firma HS256 (mínimo 32 caracteres) | valor local de desarrollo |
| `JWT_EXPIRATION` | Validez del token en ms | `3600000` |
| `INTERNAL_SERVICE_KEY` | Header `X-Internal-Key` entre servicios | valor local de desarrollo |
| `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` | Conexión MySQL | `localhost` / `3306` / `root` / `GAMSTERfredy1` |
| `GATEWAY_PORT` | Puerto publicado del gateway | `8090` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos | `localhost:4200,localhost:3000` |

> Para despliegue real, define `JWT_SECRET`, `INTERNAL_SERVICE_KEY` y las credenciales de
> MySQL con valores propios; los defaults son solo para desarrollo local.

---

## 5. Pruebas

### Unitarias + integración (103 pruebas, 0 fallos)

```bash
mvnw.cmd -B clean verify      # Windows
./mvnw -B clean verify        # Linux / macOS / Git Bash
```

| Módulo | Pruebas |
|---|---|
| auth-service | 18 (6 unit + 12 integración con `UsuarioClient` mockeado) |
| user-service | 23 (10 unit + 13 integración con H2) |
| mascota-service | 15 (6 unit + 9 integración) |
| cita-service | 25 (12 unit + 12 integración + 1 de concurrencia con 10 hilos) |
| expediente-service | 22 (9 unit + 13 integración) |

### Prueba funcional E2E

Con el stack levantado:

```bash
./test-veterinaria.sh
# o BASE_URL=http://otro-host:8090 ./test-veterinaria.sh
```

Cubre: disponibilidad, login por roles, registro (rol forzado a `CLIENTE`),
autorizaciones `401/403`, mascotas, solape de citas `409`, límite de 2 pendientes/día,
agenda del veterinario, expediente (y su conflicto), cancelación con antelación e
historial. Sale con código distinto de 0 si algo falla.

Prueba complementaria (registro, mascota, control de acceso `403` y estrés de agenda):

```bash
./test-api2.sh
```

### Carga (k6)

```bash
k6 run k6/carga.js
k6 run -e VUS=10 -e DURACION=30s k6/carga.js
```

Umbrales: `p(95) < 1000 ms` y cero logins/operaciones fallidas (métricas propias
`login_fallidos` y `operaciones_fallidas`). El `401` de la comprobación “sin token” se
marca como estado esperado para que no cuente como fallo HTTP.

---

## 6. Decisiones y restricciones documentadas

- **Ejecución local, sin Docker**: los servicios se arrancan con el Maven Wrapper y
  MySQL local; los esquemas se autocrean y los valores sensibles tienen defaults de
  desarrollo.
- **BD por servicio**: cada servicio solo conoce su esquema; no hay acceso cruzado a
  tablas. Las referencias entre servicios se resuelven por API interna
  (`/internal/**`) y se **denormalizan** en cada base: `citas.cliente_id`,
  `citas.mascota_nombre`, `citas.veterinario_nombre`, `expedientes.mascota_id`,
  `mascotas.cliente_nombre` (evita N+1 en listados).
- **BCrypt solo en `user-service`**: `auth-service` no tiene base de datos; delega la
  verificación de credenciales y el hash nunca sale de `user-service`.
- **`ddl-auto=update`**: el esquema lo genera Hibernate al arrancar cada servicio. En
  producción usar migraciones (Flyway/Liquibase).
- **Puertos**: 8090 gateway (8080 ocupado en la máquina), servicios 8081–8085.
- **Swagger por servicio** (el gateway solo enruta `/api/v1/**`).
- Los endpoints de `user-service` mantienen el contrato acordado aunque el rol efectivo
  de `/mascotas/{id}` y la agenda forzada al vet autenticado limiten el acceso cruzado
  (ver sección 2).
