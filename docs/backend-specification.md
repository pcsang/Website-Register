# Backend Technical Specification

**Scope:** This document describes the **current, actual implementation** of the Spring Boot backend at
`backend/` in this repository, as of the completion of Phase 10 of the project roadmap (Backend MVP).
It is derived directly from the source code under `backend/src/main/java/com/register/backend/`, not from
the roadmap document or `README.md`, which may describe future or partially-stale plans.

For the full phased roadmap and original product spec, see
[`java-spring-boot-angular-project-prompts.md`](../java-spring-boot-angular-project-prompts.md). For
day-to-day working rules and local environment setup (portable JDK/Maven/PostgreSQL commands), see
[`CLAUDE.md`](../CLAUDE.md) and [`CHECKLIST.md`](../CHECKLIST.md).

---

## 1. Tech Stack

Sourced from `backend/pom.xml`.

| Component | Version / Detail |
|---|---|
| Language | Java 21 |
| Build tool | Maven |
| Spring Boot | 3.4.1 (`spring-boot-starter-parent`) |
| Web layer | `spring-boot-starter-web` (Spring MVC, embedded Tomcat) |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| Monitoring | `spring-boot-starter-actuator` |
| Database driver | `org.postgresql:postgresql` (runtime scope) |
| Database | PostgreSQL |
| API docs | `springdoc-openapi-starter-webmvc-ui` 2.7.0 (Swagger UI + OpenAPI 3 JSON) |
| Testing | `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, Spring Test) |
| Packaging | `spring-boot-maven-plugin` (executable jar) |

No Spring Security dependency is present in `pom.xml` — authentication/authorization is not yet
implemented (see [Section 6](#6-cross-cutting-configuration) and [Section 9](#9-not-yet-implemented)).

Artifact coordinates: `com.register:backend:0.0.1-SNAPSHOT`.

---

## 2. Layered Architecture & Package Layout

The backend follows a strict **Controller → Service → Repository** layering under the base package
`com.register.backend`:

| Package | Purpose |
|---|---|
| `config/` | Application-wide configuration beans: CORS policy and OpenAPI/Swagger metadata. |
| `controller/` | Thin REST controllers — validate input via annotations, delegate to exactly one service call, return a response DTO. No business logic or repository access. |
| `dto/request/` | Inbound request body shapes (Java `record`s), carrying Jakarta Validation constraints. |
| `dto/response/` | Outbound response body shapes (Java `record`s) — the only shapes the API ever returns; JPA entities are never serialized directly. |
| `entity/` | JPA entities mapped to database tables. |
| `enums/` | Shared enumerations used by entities/DTOs (currently `SubmissionStatus`). |
| `exception/` | Custom exceptions, the global error response shape, and the centralized `@RestControllerAdvice` handler. |
| `mapper/` | Manual entity ↔ DTO mapping classes (no MapStruct/ModelMapper — intentionally simple, hand-written). |
| `repository/` | Spring Data JPA repository interfaces (query methods and one custom JPQL query). |
| `security/` | Reserved for future JWT-based admin authentication (Phase 16 of the roadmap). Currently empty except for a `.gitkeep` placeholder — **no security is implemented yet**. |
| `service/` | Business logic and `@Transactional` boundaries; the only layer that talks to repositories. |

Root class: `BackendApplication` (`@SpringBootApplication`, standard `main()` entry point).

---

## 3. JPA Entities

### 3.1 `Submission` (`entity/Submission.java`)

Table: `submissions`. The only entity in the system.

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
Spring Data JPA auditing) — a deliberate choice documented in `CLAUDE.md` for this single-entity project.

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

---

## 4. REST API

All endpoints are served under the embedded Tomcat server on port `8080` (`server.port` in
`application.yml`). There are two controller groups: a public controller (`/api/submissions`) and an admin
controller group (`/api/admin/**`), plus a health endpoint. No path currently requires authentication.

### 4.1 `GET /api/health`

**Controller:** `HealthController`

Simple liveness check, unrelated to Spring Boot Actuator's own health endpoint.

- **Response:** `200 OK`

| Field | Type |
|---|---|
| `status` | `String` (always `"UP"`) |

```json
{ "status": "UP" }
```

### 4.2 `POST /api/submissions`

**Controller:** `SubmissionController` → `SubmissionService.createSubmission()`

Public endpoint for end users to submit a contact/inquiry form. No authentication required.

**Request body:** `CreateSubmissionRequest`

| Field | Type | Validation | Required |
|---|---|---|---|
| `fullName` | `String` | `@NotBlank`, `@Size(max = 200)` | Yes |
| `email` | `String` | `@Email` (custom message: "must be a valid email"), `@Size(max = 255)` | No |
| `phone` | `String` | `@Size(max = 30)` | No |
| `message` | `String` | `@Size(max = 2000)` | No |

**Behavior:** Maps the request to a new `Submission` entity, forces `status = NEW` server-side (the client
cannot set status on creation), persists it, and returns the saved entity mapped to a `SubmissionResponse`.

**Response:** `201 Created`, body is `SubmissionResponse` (see [4.5](#45-submissionresponse-shape) below).

**Error cases:** `400 Bad Request` with field errors if validation fails (see
[Section 5](#5-global-error-handling)).

### 4.3 `GET /api/admin/submissions`

**Controller:** `AdminSubmissionController` → `SubmissionService.listSubmissions()`

Admin endpoint listing submissions with server-side pagination, optional search, and optional status
filter. No authentication required (not yet implemented — see [Section 9](#9-not-yet-implemented)).

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

**Error cases:** `400 Bad Request` for an invalid `status` value or an invalid `sort` field name (both
handled explicitly — see [Section 5](#5-global-error-handling)).

### 4.4 `GET /api/admin/submissions/{id}`

**Controller:** `AdminSubmissionController` → `SubmissionService.getSubmissionById()`

Retrieves a single submission by its numeric ID.

**Path parameter:** `id` (`Long`)

**Response:** `200 OK`, body is `SubmissionResponse`.

**Error cases:** `404 Not Found` if no submission exists with that ID (`ResourceNotFoundException`,
message: `"Submission not found with id: {id}"`).

### 4.5 `SubmissionResponse` shape

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

### 4.6 `PATCH /api/admin/submissions/{id}/status`

**Controller:** `AdminSubmissionController` → `SubmissionService.updateStatus()`

Updates only the `status` field of an existing submission.

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
- `404 Not Found` if the submission doesn't exist.
- `400 Bad Request` if `status` is `null` (bean validation) **or** if the JSON body contains an
  unrecognized enum string, e.g. `{"status": "BOGUS"}` (a Jackson deserialization failure, handled
  separately from bean validation — see [Section 5](#5-global-error-handling)).

### 4.7 `GET /api/admin/dashboard/summary`

**Controller:** `DashboardController` → `DashboardService.getSummary()`

Returns aggregate counts for the admin dashboard, computed via five separate `COUNT` queries against
`SubmissionRepository` (no row data loaded).

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

### 4.8 Endpoint summary table

| Method | Path | Auth | Request body | Success status |
|---|---|---|---|---|
| `GET` | `/api/health` | None | — | `200` |
| `POST` | `/api/submissions` | None | `CreateSubmissionRequest` | `201` |
| `GET` | `/api/admin/submissions` | None | — (query params) | `200` |
| `GET` | `/api/admin/submissions/{id}` | None | — | `200` |
| `PATCH` | `/api/admin/submissions/{id}/status` | None | `UpdateSubmissionStatusRequest` | `200` |
| `GET` | `/api/admin/dashboard/summary` | None | — | `200` |

"Auth: None" reflects the current state — see [Section 9](#9-not-yet-implemented). Endpoints under
`/api/admin/**` are namespaced for future authorization but are not currently protected.

---

## 5. Global Error Handling

**Source:** `exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`), `exception/ErrorResponse.java`,
`exception/ResourceNotFoundException.java`.

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

### 5.2 Exception → response mapping

| Exception | HTTP Status | `message` | `errors` | Notes |
|---|---|---|---|---|
| `MethodArgumentNotValidException` (Jakarta Bean Validation failure on `@Valid @RequestBody`) | `400` | `"Validation failed"` | field → message map | e.g. blank `fullName`, invalid `email` format, oversized field |
| `ResourceNotFoundException` (thrown explicitly by services) | `404` | Exception message, e.g. `"Submission not found with id: 5"` | omitted | Used for both `GET /{id}` and `PATCH /{id}/status` on a missing ID |
| `NoResourceFoundException` (Spring's "no route matched") | `404` | `"No handler found for {METHOD} {path}"` | omitted | Prevents unmatched routes from being swallowed by the generic `500` handler |
| `MethodArgumentTypeMismatchException` (query/path param can't convert to target type, e.g. `?status=BOGUS`) | `400` | `"Invalid value for parameter '{name}'"` | omitted | |
| `InvalidDataAccessApiUsageException` (e.g. invalid `?sort=` property name) | `400` | `"Invalid sort field"` | omitted | Spring Data's wrapping of Hibernate's unknown-attribute failure |
| `HttpMessageNotReadableException` (malformed JSON body, or an unrecognized enum string like `{"status":"BOGUS"}`) | `400` | `"Malformed request body"` | omitted | Fires before Bean Validation runs (Jackson deserialization failure) |
| Any other `Exception` (catch-all) | `500` | `"An unexpected error occurred"` | omitted | Full exception is logged server-side via SLF4J (`log.error`) with stack trace; **never** leaked to the client |

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

---

## 6. Cross-Cutting Configuration

### 6.1 CORS (`config/CorsConfig.java`)

- Implements `WebMvcConfigurer.addCorsMappings`, applied to `/api/**`.
- Allowed origins are bound from `app.cors.allowed-origins` (`application.yml`), itself backed by the
  `ALLOWED_ORIGINS` environment variable, defaulting to `http://localhost:4200`. Value is split on `,` to
  support multiple origins.
- **No wildcard (`*`) origin** — only the configured explicit origin(s) are ever echoed back.
- Allowed methods: `GET, POST, PATCH, OPTIONS`.
- Allowed headers: `Content-Type, Authorization` (the latter is future-proofed for JWT auth, not currently
  used by any endpoint).
- `allowCredentials` is left unset (default `false`) — no cookie/session-based auth is planned; a future
  JWT would travel via the `Authorization` header, which doesn't require CORS credentials mode.

### 6.2 OpenAPI / Swagger (`config/OpenApiConfig.java`)

- `springdoc-openapi-starter-webmvc-ui` is on the classpath and a single `OpenAPI` bean sets top-level
  metadata: title `"Information Collection & Admin Management System API"`, description, version `"v1"`.
- No per-endpoint annotations are used in controllers/DTOs — the spec is generated automatically from the
  existing Spring MVC mappings, request/response types, and Bean Validation annotations.
- With default springdoc settings, this exposes the machine-readable spec at `/v3/api-docs` and an
  interactive UI at `/swagger-ui.html` (or `/swagger-ui/index.html`) when the app is running.

### 6.3 Security (`security/` package)

**Empty** — contains only a `.gitkeep` placeholder. No Spring Security dependency, no authentication
filter, no login endpoint, and no authorization checks exist anywhere in the codebase. Every endpoint
listed in [Section 4](#4-rest-api), including all `/api/admin/**` routes, is currently reachable without
credentials. This package is reserved for the roadmap's JWT-based admin authentication phase, not yet
started.

### 6.4 Actuator

`spring-boot-starter-actuator` is a dependency; `application.yml` exposes only `health` and `info` over
the web (`management.endpoints.web.exposure.include: health,info`). No custom actuator configuration
beyond this exists.

### 6.5 Database / JPA configuration

From `application.yml`:

- Datasource: PostgreSQL via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` env vars (local defaults:
  `jdbc:postgresql://localhost:5432/information_db`, `postgres` / `postgres`).
- `spring.jpa.hibernate.ddl-auto: update` — schema is auto-updated (additive only; see the "Schema
  caveat" in `CLAUDE.md`).
- `show-sql: true` with `format_sql: true` (SQL logged for local development/debugging).
- `spring.data.web.pageable`: `default-page-size: 20`, `max-page-size: 100`.

---

## 7. Mapping & Service Layer Notes

- `SubmissionMapper` (`mapper/`) is a plain `@Component` with two hand-written methods: `toEntity` (request
  → new entity, used only for creation) and `toResponse` (entity → response DTO). No library-based mapping
  is used anywhere in the project.
- `SubmissionService` and `DashboardService` are the only two service classes. Both use constructor
  injection exclusively and mark read methods `@Transactional(readOnly = true)` and mutating methods
  `@Transactional`.
- `SubmissionRepository` extends `JpaRepository<Submission, Long>` and adds three query methods:
  `countByStatus`, `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan` (both Spring Data derived
  queries), and `search` (a hand-written JPQL `@Query` combining optional search + status filters with
  `(:param IS NULL OR ...)` guards, using `CAST(:search AS string)` to avoid a PostgreSQL/Hibernate
  `lower(bytea)` type-inference failure on a null bind parameter).

---

## 8. How to Run / Verify

Full commands (portable JDK/Maven/PostgreSQL setup, build, run, test) are documented in
[`CLAUDE.md`](../CLAUDE.md)'s "Commands" section — not duplicated here to avoid drift between the two
documents. In short: `cd backend`, set `JAVA_HOME`/`PATH` to the portable toolchain if no system JDK 21 is
available, ensure PostgreSQL is reachable, then `mvn spring-boot:run` (or `mvn clean verify` to build and
test).

---

## 9. Not Yet Implemented

Based on empty/reserved packages and the absence of related dependencies in `pom.xml`:

- **Authentication / Authorization** — the `security/` package is empty; there is no Spring Security
  dependency; no login endpoint exists; every `/api/admin/**` route is currently open to any caller. This
  corresponds to the roadmap's "Phase 16 — Spring Security (JWT Admin Auth)", not yet started.
- No rate limiting, request logging/auditing beyond SLF4J error logs, or additional actuator endpoints
  beyond `health`/`info` are configured.
