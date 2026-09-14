# Backend Technical Specification

**Scope:** This document describes the **current, actual implementation** of the Spring Boot backend at
`backend/` in this repository, as of the completion of Phase 16 of the project roadmap (Spring Security —
JWT Admin Auth), plus the subsequent Maven → Gradle build-tool migration.
It is derived directly from the source code under `backend/src/main/java/com/register/backend/`, not from
the roadmap document or `README.md`, which may describe future or partially-stale plans.

For the full phased roadmap and original product spec, see
[`java-spring-boot-angular-project-prompts.md`](../java-spring-boot-angular-project-prompts.md). For
day-to-day working rules and local environment setup (portable JDK/Gradle/PostgreSQL commands), see
[`CLAUDE.md`](../CLAUDE.md) and [`CHECKLIST.md`](../CHECKLIST.md).

---

## 1. Tech Stack

Sourced from `backend/build.gradle`.

| Component | Version / Detail |
|---|---|
| Language | Java 21 |
| Build tool | Gradle (Gradle Wrapper 8.11.1; migrated from Maven — no `pom.xml`/`mvnw` in this repo anymore) |
| Spring Boot | 3.4.1 (via `org.springframework.boot` + `io.spring.dependency-management` 1.1.7 plugins) |
| Web layer | `spring-boot-starter-web` (Spring MVC, embedded Tomcat) |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| Monitoring | `spring-boot-starter-actuator` |
| Security | `spring-boot-starter-security` (Spring Security 6) |
| JWT | `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.6 |
| Database driver | `org.postgresql:postgresql` (`runtimeOnly`) |
| Database | PostgreSQL |
| API docs | `springdoc-openapi-starter-webmvc-ui` 2.7.0 (Swagger UI + OpenAPI 3 JSON) |
| Testing | `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, Spring Test) + `spring-security-test` |
| Packaging | Spring Boot Gradle plugin (`bootJar` task, executable jar) |

Artifact coordinates: `com.register:backend:0.0.1-SNAPSHOT`.

---

## 2. Layered Architecture & Package Layout

The backend follows a strict **Controller → Service → Repository** layering under the base package
`com.register.backend`:

| Package | Purpose |
|---|---|
| `config/` | Application-wide configuration beans: CORS policy, OpenAPI/Swagger metadata, and `AdminUserSeeder` (startup seeding of the first admin account). |
| `controller/` | Thin REST controllers — validate input via annotations, delegate to exactly one service call, return a response DTO. No business logic or repository access. |
| `dto/request/` | Inbound request body shapes (Java `record`s), carrying Jakarta Validation constraints. |
| `dto/response/` | Outbound response body shapes (Java `record`s) — the only shapes the API ever returns; JPA entities are never serialized directly. |
| `entity/` | JPA entities mapped to database tables (`Submission`, `AdminUser`). |
| `enums/` | Shared enumerations used by entities/DTOs (currently `SubmissionStatus`). |
| `exception/` | Custom exceptions, the global error response shape, and the centralized `@RestControllerAdvice` handler. |
| `mapper/` | Manual entity ↔ DTO mapping classes (no MapStruct/ModelMapper — intentionally simple, hand-written). |
| `repository/` | Spring Data JPA repository interfaces (query methods and one custom JPQL query). |
| `security/` | JWT admin authentication: `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`. **No longer empty** — see [Section 6.3](#63-security-security-package). |
| `service/` | Business logic and `@Transactional` boundaries; the only layer that talks to repositories. |

Root class: `BackendApplication` (`@SpringBootApplication`, standard `main()` entry point).

---

## 3. JPA Entities

### 3.1 `Submission` (`entity/Submission.java`)

Table: `submissions`.

| Field | Java Type | Column | Nullable | Length / Type | Bean Validation | Notes |
|---|---|---|---|---|---|---|
| `id` | `Long` | `id` | — | — | — | `@Id`, `@GeneratedValue(strategy = IDENTITY)` |
| `fullName` | `String` | `full_name` | `NOT NULL` | `VARCHAR(200)` | `@NotBlank` | |
| `email` | `String` | `email` | nullable | `VARCHAR(255)` | none on entity | Optional field |
| `phone` | `String` | `phone` | nullable | `VARCHAR(30)` | none on entity | Optional field |
| `message` | `String` | `message` | nullable | `TEXT` (`columnDefinition = "TEXT"`) | none on entity | Optional field |
| `status` | `SubmissionStatus` | `status` | `NOT NULL` | `VARCHAR(20)`, `@Enumerated(EnumType.STRING)` | `@NotNull` | Always set server-side to `NEW` on creation; never client-supplied on create |
| `createdAt` | `LocalDateTime` | `created_at` | `NOT NULL`, `updatable = false` | — | — | Set via `@PrePersist` (`LocalDateTime.now()`), no time zone stored |
| `updatedAt` | `LocalDateTime` | `updated_at` | `NOT NULL` | — | — | Set on insert and refreshed via `@PreUpdate` on every update |

Timestamps are managed directly on the entity via `@PrePersist`/`@PreUpdate` lifecycle callbacks (not
Spring Data JPA auditing) — a deliberate choice documented in `CLAUDE.md` for this small project.

There are no `company` or `position` fields — these existed in the original roadmap spec but were removed
by an explicit product decision during Phase 4 (see `CHECKLIST.md`), and `email` was made optional rather
than required.

### 3.2 `SubmissionStatus` (`enums/SubmissionStatus.java`)

A plain Java enum with three values, persisted as `STRING` (not ordinal):

```
NEW, IN_PROGRESS, COMPLETED
```

Used by `Submission.status`, `SubmissionResponse.status`, `UpdateSubmissionStatusRequest.status`, and as an
optional query parameter on the admin list endpoint.

### 3.3 `AdminUser` (`entity/AdminUser.java`)

Table: `admin_users`. Introduced in Phase 16 for JWT admin authentication.

| Field | Java Type | Column | Nullable | Length / Type | Bean Validation | Notes |
|---|---|---|---|---|---|---|
| `id` | `Long` | `id` | — | — | — | `@Id`, `@GeneratedValue(strategy = IDENTITY)` |
| `username` | `String` | `username` | `NOT NULL`, `UNIQUE` | `VARCHAR(100)` | `@NotBlank` | |
| `passwordHash` | `String` | `password_hash` | `NOT NULL` | `VARCHAR(255)` | `@NotBlank` | **BCrypt hash only — plaintext password is never persisted** |
| `role` | `String` | `role` | `NOT NULL` | `VARCHAR(30)` | `@NotBlank` | Plain string, e.g. `"ROLE_ADMIN"` — no enum, since only one role currently exists |
| `createdAt` | `LocalDateTime` | `created_at` | `NOT NULL`, `updatable = false` | — | — | Set via `@PrePersist` |

There is no signup/registration endpoint (internal admin tool) — see
[`AdminUserSeeder`](#63-security-security-package) for how the first row gets created.

---

## 4. REST API

All endpoints are served under the embedded Tomcat server on port `8080` (`server.port` in
`application.yml`). Controller groups: a public controller (`/api/submissions`), an admin controller group
(`/api/admin/**`, now authenticated), an auth controller (`/api/auth/login`), plus a health endpoint.

### 4.1 `GET /api/health`

**Controller:** `HealthController`

Simple liveness check, unrelated to Spring Boot Actuator's own health endpoint. Public — no token.

- **Response:** `200 OK`

| Field | Type |
|---|---|
| `status` | `String` (always `"UP"`) |

```json
{ "status": "UP" }
```

### 4.2 `POST /api/submissions`

**Controller:** `SubmissionController` → `SubmissionService.createSubmission()`

Public endpoint for end users to submit a contact/inquiry form. No authentication required — and the JWT
is never sent here by the frontend either (see [`ui-specification.md`](ui-specification.md)).

**Request body:** `CreateSubmissionRequest`

| Field | Type | Validation | Required |
|---|---|---|---|
| `fullName` | `String` | `@NotBlank`, `@Size(max = 200)` | Yes |
| `email` | `String` | `@Email` (custom message: "must be a valid email"), `@Size(max = 255)` | No |
| `phone` | `String` | `@Size(max = 30)` | No |
| `message` | `String` | `@Size(max = 2000)` | No |

**Behavior:** Maps the request to a new `Submission` entity, forces `status = NEW` server-side (the client
cannot set status on creation), persists it, and returns the saved entity mapped to a `SubmissionResponse`.

**Response:** `201 Created`, body is `SubmissionResponse` (see [4.6](#46-submissionresponse-shape) below).

**Error cases:** `400 Bad Request` with field errors if validation fails (see
[Section 5](#5-global-error-handling)).

### 4.3 `POST /api/auth/login`

**Controller:** `AuthController` → `AuthService.login()`

Public endpoint. Authenticates an admin username/password and issues a signed JWT. Introduced in Phase 16.

**Request body:** `LoginRequest`

| Field | Type | Validation | Required |
|---|---|---|---|
| `username` | `String` | `@NotBlank` | Yes |
| `password` | `String` | `@NotBlank` | Yes |

**Behavior:** Looks up the `AdminUser` by username, then checks the password against the stored BCrypt
hash via `PasswordEncoder.matches()`. Both "username not found" and "wrong password" throw the same
`InvalidCredentialsException` with an identical message, so the API never reveals whether a given username
exists. On success, `JwtService.generateToken()` issues an HS256 JWT (subject = username, custom claim
`role` = the user's role, `iat`/`exp` set from `app.jwt.expiration-ms`).

**Response:** `200 OK`, body is `LoginResponse`:

| Field | Type | Description |
|---|---|---|
| `token` | `String` | Signed JWT — send as `Authorization: Bearer <token>` on subsequent admin requests |
| `username` | `String` | The authenticated admin's username |
| `role` | `String` | The authenticated admin's role, e.g. `"ROLE_ADMIN"` |

**Error cases:** `401 Unauthorized` with the standard error shape (`message`: `"Invalid username or
password"`) on bad credentials — see [Section 5](#5-global-error-handling).

### 4.4 `GET /api/admin/submissions`

**Controller:** `AdminSubmissionController` → `SubmissionService.listSubmissions()`

Admin endpoint listing submissions with server-side pagination, optional search, and optional status
filter. **Requires `Authorization: Bearer <token>` with `ROLE_ADMIN`** (see
[Section 6.3](#63-security-security-package)).

**Query parameters:**

| Param | Type | Required | Default | Description |
|---|---|---|---|---|
| `search` | `String` | No | none | Case-insensitive substring match against `fullName`, `email`, or `phone` (OR'd together), executed as a `LIKE` at the database level |
| `status` | `SubmissionStatus` (`NEW`/`IN_PROGRESS`/`COMPLETED`) | No | none (all statuses) | Exact-match status filter |
| `page` | `int` | No | `0` | Zero-based page index (Spring Data `Pageable` binding) |
| `size` | `int` | No | `20` | Page size; capped server-side at `100` (`spring.data.web.pageable.max-page-size`) |
| `sort` | `String` | No | `createdAt,desc` | Sort field/direction, e.g. `?sort=fullName,asc`; an invalid field name returns `400` |

**Behavior:** Both the search filter and the status filter are applied inside a single JPQL query
(`SubmissionRepository.search`) using `(:param IS NULL OR ...)` guards, so filtering and paging both
execute at the database level, not in application memory.

**Response:** `200 OK`, body is `PageResponse<SubmissionResponse>`:

| Field | Type | Description |
|---|---|---|
| `content` | `List<SubmissionResponse>` | The page's rows |
| `page` | `int` | Current zero-based page index |
| `size` | `int` | Page size used |
| `totalElements` | `long` | Total matching rows across all pages |
| `totalPages` | `int` | Total number of pages |

**Error cases:** `401` (missing/invalid/expired token), `400` for an invalid `status` value or an invalid
`sort` field name — see [Section 5](#5-global-error-handling).

### 4.5 `GET /api/admin/submissions/{id}`

**Controller:** `AdminSubmissionController` → `SubmissionService.getSubmissionById()`

Retrieves a single submission by its numeric ID. **Requires `Authorization: Bearer <token>` with
`ROLE_ADMIN`.**

**Path parameter:** `id` (`Long`)

**Response:** `200 OK`, body is `SubmissionResponse`.

**Error cases:** `401` (missing/invalid/expired token); `404 Not Found` if no submission exists with that
ID (`ResourceNotFoundException`, message: `"Submission not found with id: {id}"`).

### 4.6 `SubmissionResponse` shape

Used by the create, list, get-by-id, and update-status endpoints.

| Field | Type |
|---|---|
| `id` | `Long` |
| `fullName` | `String` |
| `email` | `String` (nullable) |
| `phone` | `String` (nullable) |
| `message` | `String` (nullable) |
| `status` | `SubmissionStatus` (`"NEW"` / `"IN_PROGRESS"` / `"COMPLETED"`) |
| `createdAt` | `LocalDateTime` (ISO-8601, no time zone) |
| `updatedAt` | `LocalDateTime` (ISO-8601, no time zone) |

### 4.7 `PATCH /api/admin/submissions/{id}/status`

**Controller:** `AdminSubmissionController` → `SubmissionService.updateStatus()`

Updates only the `status` field of an existing submission. **Requires `Authorization: Bearer <token>` with
`ROLE_ADMIN`.**

**Path parameter:** `id` (`Long`)

**Request body:** `UpdateSubmissionStatusRequest`

| Field | Type | Validation | Required |
|---|---|---|---|
| `status` | `SubmissionStatus` | `@NotNull` | Yes |

**Behavior:** Looks up the submission (404 if missing), sets the new status, and persists it via
`saveAndFlush()` (rather than `save()`) so that the `@PreUpdate` callback bumping `updatedAt` runs before
the response is built — avoiding a stale `updatedAt` in the response.

**Response:** `200 OK`, body is `SubmissionResponse` reflecting the new status and refreshed `updatedAt`.

**Error cases:**
- `401` (missing/invalid/expired token).
- `404 Not Found` if the submission doesn't exist.
- `400 Bad Request` if `status` is `null` (bean validation) **or** if the JSON body contains an
  unrecognized enum string, e.g. `{"status": "BOGUS"}` (a Jackson deserialization failure, handled
  separately from bean validation — see [Section 5](#5-global-error-handling)).

### 4.8 `GET /api/admin/dashboard/summary`

**Controller:** `DashboardController` → `DashboardService.getSummary()`

Returns aggregate counts for the admin dashboard, computed via five separate `COUNT` queries against
`SubmissionRepository` (no row data loaded). **Requires `Authorization: Bearer <token>` with `ROLE_ADMIN`.**

**Response:** `200 OK`, body is `DashboardSummaryResponse`:

| JSON field | Java field | Type | Description |
|---|---|---|---|
| `total` | `total` | `long` | Total submissions (`count()`) |
| `new` | `newCount` | `long` | Count with `status = NEW` (JSON key is `new`, via `@JsonProperty("new")`, since `new` is a Java keyword) |
| `inProgress` | `inProgress` | `long` | Count with `status = IN_PROGRESS` |
| `completed` | `completed` | `long` | Count with `status = COMPLETED` |
| `submittedToday` | `submittedToday` | `long` | Count with `createdAt` in `[startOfToday, startOfTomorrow)`, server-local time, matching how `createdAt` is populated (no time zone stored) |

```json
{
  "total": 42,
  "new": 10,
  "inProgress": 5,
  "completed": 27,
  "submittedToday": 3
}
```

**Error cases:** `401` (missing/invalid/expired token).

### 4.9 Endpoint summary table

| Method | Path | Auth | Request body | Success status |
|---|---|---|---|---|
| `GET` | `/api/health` | None | — | `200` |
| `POST` | `/api/submissions` | None | `CreateSubmissionRequest` | `201` |
| `POST` | `/api/auth/login` | None | `LoginRequest` | `200` |
| `GET` | `/api/admin/submissions` | **`ROLE_ADMIN`** | — (query params) | `200` |
| `GET` | `/api/admin/submissions/{id}` | **`ROLE_ADMIN`** | — | `200` |
| `PATCH` | `/api/admin/submissions/{id}/status` | **`ROLE_ADMIN`** | `UpdateSubmissionStatusRequest` | `200` |
| `GET` | `/api/admin/dashboard/summary` | **`ROLE_ADMIN`** | — | `200` |

`/swagger-ui.html`, `/v3/api-docs`, and `/actuator/health`/`/actuator/info` remain public (unauthenticated)
— only `/api/admin/**` is protected. See [Section 6.3](#63-security-security-package) for how.

---

## 5. Global Error Handling

**Source:** `exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`), `exception/ErrorResponse.java`,
`exception/ResourceNotFoundException.java`, `exception/InvalidCredentialsException.java`.

### 5.1 Error response shape

`ErrorResponse` is a `record`, serialized with `@JsonInclude(NON_NULL)` so `errors` is omitted entirely
when there are no field-level errors:

| Field | Type | Present when |
|---|---|---|
| `status` | `int` | Always — HTTP status code |
| `message` | `String` | Always — human-readable summary |
| `errors` | `Map<String, String>` | Only on Bean Validation failures (field name → message) |
| `timestamp` | `LocalDateTime` | Always — set at handler execution time |
| `path` | `String` | Always — the request URI that failed |

This exact shape is also used for `401`/`403` responses produced *inside* the Spring Security filter chain
(see [Section 6.3](#63-security-security-package)) — those don't go through
`GlobalExceptionHandler` (it never sees filter-chain exceptions), but two dedicated handler classes write
the identical JSON shape by hand so the whole API stays consistent.

### 5.2 Exception → response mapping

| Exception | HTTP Status | `message` | `errors` | Notes |
|---|---|---|---|---|
| `MethodArgumentNotValidException` (Jakarta Bean Validation failure on `@Valid @RequestBody`) | `400` | `"Validation failed"` | field → message map | e.g. blank `fullName`, invalid `email` format, oversized field |
| `InvalidCredentialsException` (bad login) | `401` | `"Invalid username or password"` | omitted | Same message whether the username doesn't exist or the password is wrong |
| `ResourceNotFoundException` (thrown explicitly by services) | `404` | Exception message, e.g. `"Submission not found with id: 5"` | omitted | Used for both `GET /{id}` and `PATCH /{id}/status` on a missing ID |
| `NoResourceFoundException` (Spring's "no route matched") | `404` | `"No handler found for {METHOD} {path}"` | omitted | Prevents unmatched routes from being swallowed by the generic `500` handler |
| `MethodArgumentTypeMismatchException` (query/path param can't convert to target type, e.g. `?status=BOGUS`) | `400` | `"Invalid value for parameter '{name}'"` | omitted | |
| `InvalidDataAccessApiUsageException` (e.g. invalid `?sort=` property name) | `400` | `"Invalid sort field"` | omitted | Spring Data's wrapping of Hibernate's unknown-attribute failure |
| `HttpMessageNotReadableException` (malformed JSON body, or an unrecognized enum string like `{"status":"BOGUS"}`) | `400` | `"Malformed request body"` | omitted | Fires before Bean Validation runs (Jackson deserialization failure) |
| Any other `Exception` (catch-all) | `500` | `"An unexpected error occurred"` | omitted | Full exception is logged server-side via SLF4J (`log.error`) with stack trace; **never** leaked to the client |

**Outside `GlobalExceptionHandler`** (written directly by security filter-chain components, same JSON
shape):

| Source | HTTP Status | `message` |
|---|---|---|
| `RestAuthenticationEntryPoint` (missing/invalid/expired token on a protected route) | `401` | `"Authentication required"` |
| `RestAccessDeniedHandler` (valid token, wrong role — not currently reachable since only one role exists) | `403` | `"Access denied"` |

### 5.3 Example: validation error

```json
{
  "status": 400,
  "message": "Validation failed",
  "errors": {
    "fullName": "must not be blank"
  },
  "timestamp": "2026-09-14T10:15:30",
  "path": "/api/submissions"
}
```

### 5.4 Example: not found

```json
{
  "status": 404,
  "message": "Submission not found with id: 999",
  "timestamp": "2026-09-14T10:15:30",
  "path": "/api/admin/submissions/999"
}
```

### 5.5 Example: unauthenticated admin request

```json
{
  "status": 401,
  "message": "Authentication required",
  "timestamp": "2026-09-14T10:15:30",
  "path": "/api/admin/dashboard/summary"
}
```

---

## 6. Cross-Cutting Configuration

### 6.1 CORS (`config/CorsConfig.java`)

- Implements `WebMvcConfigurer.addCorsMappings`, applied to `/api/**`.
- Allowed origins are bound from `app.cors.allowed-origins` (`application.yml`), itself backed by the
  `ALLOWED_ORIGINS` environment variable, defaulting to `http://localhost:4200`. Value is split on `,` to
  support multiple origins.
- **No wildcard (`*`) origin** — only the configured explicit origin(s) are ever echoed back.
- Allowed methods: `GET, POST, PATCH, OPTIONS`.
- Allowed headers: `Content-Type, Authorization` (`Authorization` is now actively used, by the JWT bearer
  token).
- `allowCredentials` is left unset (default `false`) — auth is a bearer token via the `Authorization`
  header, not cookies, so CORS credentials mode isn't needed. `SecurityConfig` explicitly permits `OPTIONS`
  preflight requests without authentication so CORS preflight to protected `/api/admin/**` routes isn't
  itself blocked by the security filter chain.

### 6.2 OpenAPI / Swagger (`config/OpenApiConfig.java`)

- `springdoc-openapi-starter-webmvc-ui` is on the classpath and a single `OpenAPI` bean sets top-level
  metadata: title `"Information Collection & Admin Management System API"`, description, version `"v1"`.
- No per-endpoint annotations are used in controllers/DTOs — the spec is generated automatically from the
  existing Spring MVC mappings, request/response types, and Bean Validation annotations.
- With default springdoc settings, this exposes the machine-readable spec at `/v3/api-docs` and an
  interactive UI at `/swagger-ui.html` (or `/swagger-ui/index.html`) when the app is running. Both remain
  public — `SecurityConfig` only protects `/api/admin/**`.

### 6.3 Security (`security/` package)

Implemented in Phase 16. Stateless JWT authentication protecting `/api/admin/**`.

**`SecurityConfig`** (`@Configuration`, `@EnableWebSecurity`) builds the `SecurityFilterChain`:
- CSRF disabled (not applicable — stateless, no cookies).
- `SessionCreationPolicy.STATELESS` — no `HttpSession` ever created.
- Authorization rule: `OPTIONS` always permitted (CORS preflight); `/api/admin/**` requires
  `hasRole("ADMIN")` (i.e. authority `ROLE_ADMIN`); everything else `permitAll()` — a deliberate allowlist
  choice so routes that were already public (health, Swagger, actuator, login, the public submission
  endpoint) aren't accidentally locked down by this phase.
- `JwtAuthenticationFilter` is registered via `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`.
- `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` wired in for 401/403 (see
  [Section 5.2](#52-exception--response-mapping)).
- Exposes the app's single `PasswordEncoder` bean (`BCryptPasswordEncoder`).
- **No `UserDetailsService`/`AuthenticationManager` bean** — login is handled directly in `AuthService`
  against `AdminUserRepository` + the password encoder, and per-request authentication trusts the signed
  JWT's own claims with no database lookup. This is a deliberate simplification for a single-role admin
  app; it also means there is **no server-side token revocation** — an issued token remains valid until it
  expires, there is no logout-side blacklist.

**`JwtService`** — encodes/decodes the HS256 JWT. Signing key from `app.jwt.secret` (`JWT_SECRET` env var,
must be ≥32 bytes for HS256), expiration from `app.jwt.expiration-ms` (`JWT_EXPIRATION_MS`, default
`3600000` = 1 hour). Token subject = username, custom claim `role` = the user's role string.

**`JwtAuthenticationFilter`** (`OncePerRequestFilter`) — on every request, if an `Authorization: Bearer
<token>` header is present and parses/verifies successfully, populates `SecurityContextHolder` with a
`UsernamePasswordAuthenticationToken` built from the token's `sub`/`role` claims. A missing, malformed, or
expired token is **not** treated as an error here — the request simply continues unauthenticated, and it's
`SecurityConfig`'s authorization rules (public endpoint proceeds; protected endpoint gets rejected by
`RestAuthenticationEntryPoint`) that decide the outcome.

**`AdminUserSeeder`** (`config/AdminUserSeeder.java`, an `ApplicationRunner`) — since there is no
signup/registration endpoint, this creates exactly one `AdminUser` on startup from
`app.admin.username`/`app.admin.password` (`ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars), hashed with the
same `PasswordEncoder`, **only if the `admin_users` table is currently empty**. Safe on every restart — it
never overwrites or duplicates an existing account.

**Login flow** (`POST /api/auth/login` → `AuthController` → `AuthService.login()`): look up `AdminUser` by
username → `PasswordEncoder.matches()` against the stored hash → on success, `JwtService.generateToken()`
→ `LoginResponse{token, username, role}`. On failure (username not found, or password mismatch), the same
`InvalidCredentialsException`/message either way, so the API never reveals whether a username exists.

**Per-request flow** for a protected route: `JwtAuthenticationFilter` parses the bearer token (if present)
→ `SecurityConfig`'s `hasRole("ADMIN")` check on `/api/admin/**` → controller runs normally if authorized,
or `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` writes the 401/403 body otherwise.

### 6.4 Actuator

`spring-boot-starter-actuator` is a dependency; `application.yml` exposes only `health` and `info` over
the web (`management.endpoints.web.exposure.include: health,info`). No custom actuator configuration
beyond this exists. Actuator endpoints remain public — `SecurityConfig` only protects `/api/admin/**`.

### 6.5 Database / JPA configuration

From `application.yml`:

- Datasource: PostgreSQL via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` env vars (local defaults:
  `jdbc:postgresql://localhost:5432/information_db`, `postgres` / `postgres`).
- `spring.jpa.hibernate.ddl-auto: update` — schema is auto-updated (additive only; see the "Schema
  caveat" in `CLAUDE.md`).
- `show-sql: true` with `format_sql: true` (SQL logged for local development/debugging).
- `spring.data.web.pageable`: `default-page-size: 20`, `max-page-size: 100`.

### 6.6 Security-related environment variables

| Variable | Default (local dev only) | Purpose |
|---|---|---|
| `JWT_SECRET` | a placeholder string, clearly marked non-production | HMAC signing secret for admin JWTs |
| `JWT_EXPIRATION_MS` | `3600000` (1 hour) | Token lifetime |
| `ADMIN_USERNAME` | `admin` | Seeded admin username |
| `ADMIN_PASSWORD` | a placeholder string, clearly marked non-production | Seeded admin password (BCrypt-hashed before storage, plaintext never persisted) |

**Always override all four outside local development** — see `README.md` for the full env var table
shared with the datasource/CORS variables.

---

## 7. Mapping & Service Layer Notes

- `SubmissionMapper` (`mapper/`) is a plain `@Component` with two hand-written methods: `toEntity` (request
  → new entity, used only for creation) and `toResponse` (entity → response DTO). No library-based mapping
  is used anywhere in the project.
- `SubmissionService`, `DashboardService`, and `AuthService` are the three service classes. All use
  constructor injection exclusively; read methods are `@Transactional(readOnly = true)` and mutating
  methods `@Transactional`.
- `SubmissionRepository` extends `JpaRepository<Submission, Long>` and adds three query methods:
  `countByStatus`, `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan` (both Spring Data derived
  queries), and `search` (a hand-written JPQL `@Query` combining optional search + status filters with
  `(:param IS NULL OR ...)` guards, using `CAST(:search AS string)` to avoid a PostgreSQL/Hibernate
  `lower(bytea)` type-inference failure on a null bind parameter).
- `AdminUserRepository` extends `JpaRepository<AdminUser, Long>` and adds `findByUsername` (used by login)
  and relies on the inherited `count()` (used by `AdminUserSeeder` to check "is the table empty").

---

## 8. How to Run / Verify

Full commands (portable JDK/PostgreSQL setup, build, run, test) are documented in
[`CLAUDE.md`](../CLAUDE.md)'s "Commands" section — not duplicated here to avoid drift between the two
documents. In short: `cd backend`, set `JAVA_HOME`/`PATH` to the portable JDK if no system JDK 21 is
available, ensure PostgreSQL is reachable, then `./gradlew bootRun` (or `./gradlew clean build` to build
and test). No system-wide Gradle install is needed — the committed Gradle Wrapper (`gradlew`/`gradlew.bat`)
self-bootstraps its pinned version on first run.

---

## 9. Not Yet Implemented

- **Backend unit/integration test phases (roadmap Phases 18–19)** — beyond what already exists
  (`BackendApplicationTests`, `HealthControllerTest`, `GlobalExceptionHandlerTest`, `SubmissionServiceTest`,
  `DashboardServiceTest`, `SecurityIntegrationTest`), the roadmap's dedicated unit-test and integration-test
  phases haven't been done as their own scoped pass.
- **Docker / deployment (roadmap Phases 20–24)** — no `Dockerfile`, no Docker Compose, no production
  database (Neon) or hosting (Render/Vercel) configured yet.
- **Token revocation** — an issued JWT is valid until it expires; there is no server-side session/blacklist
  to invalidate a token early (e.g. on logout, the frontend just discards its local copy — the token itself
  remains technically valid until expiry if captured beforehand). Documented as an accepted tradeoff for
  this app's size in `CHECKLIST.md`'s Phase 16 log.
- No rate limiting, request logging/auditing beyond SLF4J error logs, or additional actuator endpoints
  beyond `health`/`info` are configured.
