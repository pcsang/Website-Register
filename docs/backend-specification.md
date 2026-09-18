# Backend Technical Specification

**Scope:** This document describes the **current, actual implementation** of the Spring Boot backend at
`backend/` in this repository, as of the completion of **Plan 2 — Full DriveUp domain adoption** (D1–D6),
which itself builds on roadmap Phases 1–23 (backend MVP through JWT auth, Gradle migration, backend/
integration testing, Docker, Docker Compose, Neon/Flyway, and a live Render deployment). It is derived
directly from the source code under `backend/src/main/java/com/register/backend/`, not from the roadmap
document or `README.md`, which may describe future or partially-stale plans.

For the full original phased roadmap, see
[`java-spring-boot-angular-project-prompts.md`](../java-spring-boot-angular-project-prompts.md). For the
DriveUp UI/UX redesign that Plan 2 implements, see
[`docs/planning/plan-2-full-redesign-driveup.md`](planning/plan-2-full-redesign-driveup.md). For day-to-day
working rules and local environment setup, see [`CLAUDE.md`](../CLAUDE.md) and
[`CHECKLIST.md`](../CHECKLIST.md).

---

## 1. Tech Stack

Sourced from `backend/build.gradle`.

| Component | Version / Detail |
|---|---|
| Language | Java 21 |
| Build tool | Gradle (Gradle Wrapper 8.11.1) |
| Spring Boot | 3.4.1 (via `org.springframework.boot` + `io.spring.dependency-management` 1.1.7 plugins) |
| Web layer | `spring-boot-starter-web` (Spring MVC, embedded Tomcat) |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) |
| Schema migrations | Flyway (`org.flywaydb:flyway-database-postgresql`) — see [Section 6.5](#65-database--jpa-configuration) |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| Monitoring | `spring-boot-starter-actuator` |
| Security | `spring-boot-starter-security` (Spring Security 6) |
| JWT | `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.6 |
| Database driver | `org.postgresql:postgresql` (`runtimeOnly`) |
| Database | PostgreSQL (local dev/Docker Compose: a real Postgres instance; production: Neon) |
| API docs | `springdoc-openapi-starter-webmvc-ui` 2.7.0 (Swagger UI + OpenAPI 3 JSON) |
| Testing | `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, Spring Test) + `spring-security-test` + `com.h2database:h2` (integration tests only, see [Section 9](#9-testing)) |
| Packaging | Spring Boot Gradle plugin (`bootJar` task, executable jar); also Dockerized (multi-stage, see [Section 10](#10-docker--deployment)) |

Artifact coordinates: `com.register:backend:0.0.1-SNAPSHOT`. Live production instance:
`https://backed-website-register.onrender.com`.

---

## 2. Layered Architecture & Package Layout

The backend follows a strict **Controller → Service → Repository** layering under the base package
`com.register.backend`:

| Package | Purpose |
|---|---|
| `config/` | Application-wide configuration beans: CORS policy, OpenAPI/Swagger metadata, `AdminUserSeeder` (startup seeding of the first admin account). |
| `controller/` | Thin REST controllers — validate input via annotations, delegate to exactly one service call, return a response DTO. No business logic or repository access. |
| `dto/request/` | Inbound request body shapes (Java `record`s), carrying Jakarta Validation constraints. |
| `dto/response/` | Outbound response body shapes (Java `record`s) — the only shapes the API ever returns; JPA entities are never serialized directly. |
| `entity/` | JPA entities mapped to database tables (`Submission`, `AdminUser`, `Course`, `DashboardSettings`). |
| `enums/` | Shared enumerations used by entities/DTOs (`SubmissionStatus`, `LicenseClass`, `CourseAvailabilityStatus`). |
| `exception/` | Custom exceptions, the global error response shape, and the centralized `@RestControllerAdvice` handler. |
| `mapper/` | Manual entity ↔ DTO mapping classes (no MapStruct/ModelMapper — intentionally simple, hand-written). |
| `repository/` | Spring Data JPA repository interfaces (derived queries, JPQL `@Query`s, and one native SQL projection query). |
| `security/` | JWT admin authentication: `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`. |
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
| `message` | `String` | `message` | nullable | `TEXT` | none on entity | Optional field |
| `status` | `SubmissionStatus` | `status` | `NOT NULL` | `VARCHAR(20)`, `@Enumerated(EnumType.STRING)` | `@NotNull` | Server-set to `PENDING_CONSULTATION` on creation; never client-supplied |
| `courseId` | `Long` | `course_id` | nullable | `BIGINT`, FK → `courses(id)` | none on entity | Plain FK id field, not a JPA relationship (matches this project's style — no `@ManyToOne` anywhere); nullable, a submission can exist before a specific course is chosen |
| `createdAt` | `LocalDateTime` | `created_at` | `NOT NULL`, `updatable = false` | — | — | Set via `@PrePersist` (`LocalDateTime.now()`), no time zone stored |
| `updatedAt` | `LocalDateTime` | `updated_at` | `NOT NULL` | — | — | Set on insert and refreshed via `@PreUpdate` on every update |

Timestamps are managed directly on the entity via `@PrePersist`/`@PreUpdate` lifecycle callbacks (not
Spring Data JPA auditing) — a deliberate small-project choice, documented in `CLAUDE.md`.

There are no `company` or `position` fields (removed by an explicit product decision during Phase 4).
Entity name deliberately kept as `Submission` (not renamed `Student`) through the DriveUp redesign — see
`docs/planning/plan-2-full-redesign-driveup.md`'s "Decisions Needed" table.

### 3.2 `SubmissionStatus` (`enums/SubmissionStatus.java`)

**Four values** (migrated from an earlier 3-state model — see [Section 8.2](#82-v3extend_submissionssql)):

```
PENDING_CONSULTATION, CONFIRMED, IN_PROGRESS, GRADUATED
```

`CONFIRMED`, `IN_PROGRESS`, and `GRADUATED` count as a "registered seat" for a course's `seatsRegistered`
(see `CourseService.REGISTERED_STATUSES`) and toward the dashboard's estimated-revenue figure;
`PENDING_CONSULTATION` is an inquiry only, not yet a confirmed registration. Used by `Submission.status`,
`SubmissionResponse.status`, `UpdateSubmissionStatusRequest.status`, and as an optional query parameter on
the admin list endpoint.

### 3.3 `AdminUser` (`entity/AdminUser.java`)

Table: `admin_users`. Unchanged since Phase 16.

| Field | Java Type | Column | Nullable | Length / Type | Bean Validation | Notes |
|---|---|---|---|---|---|---|
| `id` | `Long` | `id` | — | — | — | `@Id`, `@GeneratedValue(strategy = IDENTITY)` |
| `username` | `String` | `username` | `NOT NULL`, `UNIQUE` | `VARCHAR(100)` | `@NotBlank` | |
| `passwordHash` | `String` | `password_hash` | `NOT NULL` | `VARCHAR(255)` | `@NotBlank` | **BCrypt hash only** |
| `role` | `String` | `role` | `NOT NULL` | `VARCHAR(30)` | `@NotBlank` | Plain string, e.g. `"ROLE_ADMIN"` — no enum, only one role exists |
| `createdAt` | `LocalDateTime` | `created_at` | `NOT NULL`, `updatable = false` | — | — | Set via `@PrePersist` |

No signup endpoint — see [`AdminUserSeeder`](#63-security-security-package).

### 3.4 `Course` (`entity/Course.java`)

Table: `courses`. Introduced in Plan 2 / D1 for the DriveUp course catalog.

| Field | Java Type | Column | Nullable | Length / Type | Bean Validation | Notes |
|---|---|---|---|---|---|---|
| `id` | `Long` | `id` | — | — | — | `@Id`, `@GeneratedValue(strategy = IDENTITY)` |
| `name` | `String` | `name` | `NOT NULL` | `VARCHAR(200)` | `@NotBlank` | |
| `licenseClass` | `LicenseClass` | `license_class` | `NOT NULL` | `VARCHAR(20)`, `@Enumerated(EnumType.STRING)` | `@NotNull` | `B1` / `B2` / `C` |
| `price` | `BigDecimal` | `price` | `NOT NULL` | `NUMERIC(12,2)` | `@NotNull @PositiveOrZero` | Money — never `float`/`double` |
| `durationMonths` | `Integer` | `duration_months` | `NOT NULL` | `INTEGER` | `@NotNull @Positive` | |
| `practiceHours` | `Integer` | `practice_hours` | `NOT NULL` | `INTEGER` | `@NotNull @Positive` | |
| `description` | `String` | `description` | nullable | `TEXT` | none | |
| `branch` | `String` | `branch` | nullable | `VARCHAR(100)` | none | Plain string field, not a separate `Branch` entity (deliberate — see the D2 "Decisions Needed" table) |
| `teacherName` | `String` | `teacher_name` | nullable | `VARCHAR(200)` | none | Plain string field, not a separate `Teacher` entity (the mockup's "Giáo viên & Xe" module is explicitly out of scope) |
| `seatsTotal` | `Integer` | `seats_total` | `NOT NULL` | `INTEGER` | `@NotNull @Positive` | |
| `startDate` | `LocalDate` | `start_date` | `NOT NULL` | `DATE` | `@NotNull` | |
| `createdAt` / `updatedAt` | `LocalDateTime` | `created_at` / `updated_at` | `NOT NULL` | — | — | Same `@PrePersist`/`@PreUpdate` pattern as `Submission` |

**Deliberately not stored**: `seatsRegistered` and a derived availability status. Both are computed live
(see [`CourseResponse`](#46-courseresponse-shape)) from `Submission.courseId`/`status` via a `COUNT`
query, never a stored counter — storing one would create a second source of truth that could drift from
the real submission rows.

### 3.5 `DashboardSettings` (`entity/DashboardSettings.java`)

Table: `dashboard_settings`. Introduced in Plan 2 / D3. A **single fixed-id row** (`id = 1`, not an
auto-generated identity — there is only ever one row), seeded by `V4__add_dashboard_settings.sql`.

| Field | Java Type | Column | Nullable | Length / Type | Notes |
|---|---|---|---|---|---|
| `id` | `Long` | `id` | — | fixed value `1` | |
| `passRatePercent` | `BigDecimal` | `pass_rate_percent` | nullable | `NUMERIC(5,2)`, `CHECK (0–100)` | **Not derived from any real data** — nothing in this system tracks exam results. Admin-entered via `PATCH /api/admin/dashboard/settings`. `NULL` by default ("not yet configured"), deliberately not seeded with a fake realistic-looking number. |
| `examCount` | `Integer` | `exam_count` | nullable | `INTEGER`, `CHECK (≥ 0)` | Optional companion figure for the "trên N lượt thi" framing. Also `NULL` by default. |
| `updatedAt` | `LocalDateTime` | `updated_at` | `NOT NULL` | — | Touched on every insert/update |

---

## 4. REST API

All endpoints are served under the embedded Tomcat server. `server.port` reads `${PORT:8080}` — `8080`
locally/Docker Compose, whatever Render assigns in production. Controller groups: public (`/api/health`,
`/api/submissions`, `/api/courses`, `/api/auth/login`), admin (`/api/admin/**`, JWT-protected).

### 4.1 `GET /api/health`

**Controller:** `HealthController`. Simple liveness check, unrelated to Actuator's own health endpoint.
Public. **Response:** `200 OK`, `{ "status": "UP" }`.

### 4.2 `POST /api/submissions`

**Controller:** `SubmissionController` → `SubmissionService.createSubmission()`. Public — no JWT sent here
either (see [`ui-specification.md`](ui-specification.md)).

**Request body:** `CreateSubmissionRequest`

| Field | Type | Validation | Required |
|---|---|---|---|
| `fullName` | `String` | `@NotBlank`, `@Size(max = 200)` | Yes |
| `email` | `String` | `@Email`, `@Size(max = 255)` | No |
| `phone` | `String` | `@Size(max = 30)` | No |
| `message` | `String` | `@Size(max = 2000)` | No |
| `courseId` | `Long` | none | No — added in D2; the public landing page resolves a selected license class to a `courseId` client-side before sending |

**Behavior:** Maps to a new `Submission`, forces `status = PENDING_CONSULTATION` server-side, persists,
returns `SubmissionResponse`. **Response:** `201 Created`. **Errors:** `400` on validation failure.

### 4.3 `GET /api/courses`

**Controller:** `CourseController` → `CourseService.listCourses()`. **Public**, unauthenticated — read-only
listing for the public landing page's pricing section.

**Query parameters:** standard pagination (`page`/`size`/`sort`, default `startDate` ascending — soonest
courses first). No filters (that's the admin endpoint below).

**Response:** `200 OK`, `PageResponse<CourseResponse>` (see [4.6](#46-courseresponse-shape)).

### 4.4 `POST /api/auth/login`

Unchanged since Phase 16 — see [Section 6.3](#63-security-security-package) for the full flow.

### 4.5 Admin submission endpoints (`AdminSubmissionController`, all `ROLE_ADMIN`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/admin/submissions` | Query params: `search`, `status` (now one of the 4 new values), **`courseId`** (new in D2), `page`/`size`/`sort` (default `createdAt` desc). All filters applied via one JPQL query with `(:param IS NULL OR ...)` guards. |
| `GET` | `/api/admin/submissions/{id}` | `404` if missing. |
| `PATCH` | `/api/admin/submissions/{id}/status` | Body: `UpdateSubmissionStatusRequest {status}`. `400` on `null`/unrecognized status, `404` if missing. |

### 4.6 `CourseResponse` shape

Used by all course endpoints (public and admin).

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | |
| `name` | `String` | |
| `licenseClass` | `LicenseClass` (`"B1"`/`"B2"`/`"C"`) | |
| `price` | `BigDecimal` | |
| `durationMonths` / `practiceHours` | `Integer` | |
| `description` / `branch` / `teacherName` | `String` (nullable) | |
| `seatsTotal` | `Integer` | |
| `seatsRegistered` | `long` | **Computed live** — `COUNT` of `Submission`s for this course with a registered-seat status (`CONFIRMED`/`IN_PROGRESS`/`GRADUATED`); never stored |
| `availabilityStatus` | `CourseAvailabilityStatus` (`AVAILABLE`/`FILLING_UP`/`FULL`) | Derived from `seatsRegistered`/`seatsTotal`: `< 70%` → `AVAILABLE`, `70–99%` → `FILLING_UP`, `100%+` → `FULL`. Vietnamese display strings ("Còn chỗ"/"Sắp đầy"/"Đã đầy") are a frontend concern, same convention as `SubmissionStatus`. |
| `startDate` | `LocalDate` | |
| `createdAt` / `updatedAt` | `LocalDateTime` | |

### 4.7 Admin course endpoints (`AdminCourseController`, all `ROLE_ADMIN`)

| Method | Path | Body | Notes |
|---|---|---|---|
| `GET` | `/api/admin/courses` | — | Query params: `licenseClass`, `branch` (both optional, exact-match — **no status/availability filter**, that's derived data the repository can't filter on directly), `page`/`size`/`sort` (default `createdAt` desc). |
| `GET` | `/api/admin/courses/{id}` | — | `404` if missing. |
| `POST` | `/api/admin/courses` | `CreateCourseRequest` | `201 Created`. |
| `PATCH` | `/api/admin/courses/{id}` | `UpdateCourseRequest` | Full-replacement update (all editable fields, same validation as create) — not a sparse partial patch. `404` if missing. |

`CreateCourseRequest`/`UpdateCourseRequest` fields mirror `Course`'s DB constraints exactly (see
[3.4](#34-course-entitycoursejava)): `name`, `licenseClass`, `price`, `durationMonths`, `practiceHours`,
`description`, `branch`, `teacherName`, `seatsTotal`, `startDate`.

### 4.8 Admin dashboard endpoints (`DashboardController`, all `ROLE_ADMIN`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/admin/dashboard/summary` | **Unchanged endpoint, changed shape** — see [4.9](#49-dashboardsummaryresponse-shape-updated-in-d2). |
| `GET` | `/api/admin/dashboard/overview` | New in D3 — see [4.10](#410-dashboardoverviewresponse-shape-new-in-d3). |
| `GET` | `/api/admin/dashboard/settings` | Returns the current `DashboardSettingsResponse` (`passRatePercent`, `examCount`, `updatedAt` — `null` fields if never configured). |
| `PATCH` | `/api/admin/dashboard/settings` | Body: `UpdateDashboardSettingsRequest {passRatePercent, examCount}`. Overwrites both. |

### 4.9 `DashboardSummaryResponse` shape (updated in D2)

**Breaking change** from the original 3-state shape, necessary fallout of `SubmissionStatus`'s enum
change (D2) — not itself part of D3's "overview" work.

```json
{
  "total": 42,
  "pendingConsultation": 10,
  "confirmed": 3,
  "inProgress": 5,
  "graduated": 24,
  "submittedToday": 3
}
```

Computed via five separate `COUNT` queries (one per status + total), same pattern as before.

### 4.10 `DashboardOverviewResponse` shape (new in D3)

```json
{
  "monthlyRegistrations": [
    { "month": "2026-04", "count": 0 },
    { "month": "2026-09", "count": 23 }
  ],
  "upcomingCourses": [ /* CourseResponse[] */ ],
  "estimatedRevenueThisMonth": 128100000.00,
  "settings": { "passRatePercent": 98.2, "examCount": 640, "updatedAt": "2026-09-18T20:08:10" }
}
```

| Field | Computation |
|---|---|
| `monthlyRegistrations` | Last 6 calendar months including the current one, oldest first. Grouped via a **native SQL** query (`date_trunc('month', created_at)`, `SubmissionRepository.countRegistrationsByMonthSince`) — months with zero submissions are absent from the SQL result and zero-filled by `DashboardService` so the response always has exactly 6 entries. |
| `upcomingCourses` | Courses with `startDate >= today`, soonest first, capped at `app.dashboard.upcoming-courses-limit` (`DASHBOARD_UPCOMING_COURSES_LIMIT` env var, default `4`). Reuses `CourseResponse` — no separate DTO. |
| `estimatedRevenueThisMonth` | Sum of `Course.price` × count, for submissions created since the start of the current calendar month with a registered-seat status and a non-null `courseId`. **An estimate, not real payment data** — this system has no payment/transaction concept. |
| `settings` | The current `DashboardSettings` row, verbatim. |

### 4.11 Endpoint summary table

| Method | Path | Auth | Success |
|---|---|---|---|
| `GET` | `/api/health` | None | `200` |
| `POST` | `/api/submissions` | None | `201` |
| `GET` | `/api/courses` | None | `200` |
| `POST` | `/api/auth/login` | None | `200` |
| `GET` | `/api/admin/submissions` | `ROLE_ADMIN` | `200` |
| `GET` | `/api/admin/submissions/{id}` | `ROLE_ADMIN` | `200` |
| `PATCH` | `/api/admin/submissions/{id}/status` | `ROLE_ADMIN` | `200` |
| `GET` | `/api/admin/courses` | `ROLE_ADMIN` | `200` |
| `GET` | `/api/admin/courses/{id}` | `ROLE_ADMIN` | `200` |
| `POST` | `/api/admin/courses` | `ROLE_ADMIN` | `201` |
| `PATCH` | `/api/admin/courses/{id}` | `ROLE_ADMIN` | `200` |
| `GET` | `/api/admin/dashboard/summary` | `ROLE_ADMIN` | `200` |
| `GET` | `/api/admin/dashboard/overview` | `ROLE_ADMIN` | `200` |
| `GET`/`PATCH` | `/api/admin/dashboard/settings` | `ROLE_ADMIN` | `200` |

`/swagger-ui.html`, `/v3/api-docs`, `/actuator/health`/`/actuator/info` also remain public.

---

## 5. Global Error Handling

**Unchanged since Phase 16** — see `exception/GlobalExceptionHandler.java`, `ErrorResponse.java`. Shape:
`{status, message, errors, timestamp, path}` (`errors` omitted unless present). Every new endpoint in
Plan 2 (Course, Dashboard overview/settings) funnels through the same handler — `ResourceNotFoundException`
→ `404` (e.g. "Course not found with id: {id}"), Bean Validation failures → `400` with field errors, same
as `Submission`. No new exception types were needed.

---

## 6. Cross-Cutting Configuration

### 6.1 CORS (`config/CorsConfig.java`)

Unchanged since Phase 10/16 — `app.cors.allowed-origins` (`ALLOWED_ORIGINS` env var), no wildcard, methods
`GET, POST, PATCH, OPTIONS`, headers `Content-Type, Authorization`.

### 6.2 OpenAPI / Swagger (`config/OpenApiConfig.java`)

Unchanged — auto-generated spec at `/v3/api-docs`, UI at `/swagger-ui.html`, both public.

### 6.3 Security (`security/` package)

Unchanged since Phase 16 — stateless JWT, `SecurityConfig` protects `/api/admin/**` with `hasRole("ADMIN")`,
everything else `permitAll()`. The new `/api/admin/courses/**` and `/api/admin/dashboard/**` routes fall
under the existing `/api/admin/**` rule automatically — no `SecurityConfig` changes were needed for Plan 2.
`/api/courses` (public) falls under the existing catch-all `permitAll()`. See the Phase 16 login/
per-request flow description in this doc's git history, or `CLAUDE.md`, for the full walkthrough — not
repeated here since nothing changed.

### 6.4 Actuator

Unchanged — `health`/`info` exposed, public.

### 6.5 Database / JPA configuration

**Schema ownership changed in Phase 22**: Flyway now owns the schema; `spring.jpa.hibernate.ddl-auto` is
`validate` (Hibernate only checks entities match the DB, never alters it) in the default/`prod` profiles.
Migrations live in `backend/src/main/resources/db/migration/`:

| Migration | Adds |
|---|---|
| `V1__init_schema.sql` | Baseline: `submissions`, `admin_users` (Phase 22) |
| `V2__add_courses_table.sql` | `courses` table (D1) |
| `V3__extend_submissions.sql` | `submissions.course_id` FK + 4-state status remap, including a **real data migration** for any existing rows (D2) — see [Section 8.2](#82-v3extend_submissionssql) |
| `V4__add_dashboard_settings.sql` | `dashboard_settings` table, single seeded row (D3) |
| `V5__seed_landing_courses.sql` | Seeds the 3 fixed course packages (B1/B2/C) shown on the public landing page, so that section isn't empty on a fresh database (D6) |

`spring.flyway.baseline-on-migrate: true` — lets Flyway adopt a pre-existing (pre-Flyway) database without
failing on "table already exists."

**The `test` Spring profile is the one exception**: `application-test.yml` (H2, in-memory,
`MODE=PostgreSQL`) disables Flyway (`spring.flyway.enabled: false`) and keeps `ddl-auto: create-drop`,
generating schema straight from the entities each test run — deliberate, per the Phase 19 hermetic-test
decision (H2 needs no migration history, and letting Hibernate generate its schema directly from the
*current* entity definitions is simpler and always in sync).

Datasource: `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars (local default:
`jdbc:postgresql://localhost:5432/information_db`, `postgres`/`postgres`; production: a Neon connection
string with `?sslmode=require`).

### 6.6 Production profile (`application-prod.yml`, Phase 22)

Activated via `SPRING_PROFILES_ACTIVE=prod` (set on Render): `show-sql: false`, `open-in-view: false`,
Hikari `maximum-pool-size: 5` (sized for Neon's free-tier connection limits).

### 6.7 Environment variables

| Variable | Default (local dev only) | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres | Datasource |
| `ALLOWED_ORIGINS` | `http://localhost:4200` | CORS |
| `JWT_SECRET` | dev-only placeholder | JWT signing (≥32 bytes) |
| `JWT_EXPIRATION_MS` | `3600000` (1h) | Token lifetime |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / dev-only placeholder | Seeded admin account |
| `PORT` | `8080` | Listen port — Render assigns this dynamically |
| `DASHBOARD_UPCOMING_COURSES_LIMIT` | `4` | Rows in the overview's upcoming-courses list |

**Always override the non-`PORT`/non-limit ones outside local development** — see `README.md`.

---

## 7. Mapping & Service Layer Notes

- `SubmissionMapper`, `CourseMapper` (`mapper/`) — plain `@Component`s, hand-written `toEntity`/`toResponse`
  (and `CourseMapper.applyUpdate` for the admin update endpoint). No library-based mapping anywhere.
- Five service classes: `SubmissionService`, `DashboardService`, `AuthService`, `CourseService` (D1),
  all constructor injection, `@Transactional(readOnly = true)` on reads, `@Transactional` on writes.
- `CourseService.REGISTERED_STATUSES` (`CONFIRMED`/`IN_PROGRESS`/`GRADUATED`) is `public static final` and
  reused as-is by `DashboardService`'s revenue calculation — one source of truth for "what counts as a
  registered seat," not duplicated.
- `SubmissionRepository` — see [4.5](#45-admin-submission-endpoints-adminsubmissioncontroller-all-role_admin)
  for `search`; also `countByCourseIdAndStatusIn` (a course's live `seatsRegistered`),
  `findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull` (revenue calc),
  `countRegistrationsByMonthSince` (native SQL, month-grouped chart data).
- `CourseRepository.search(licenseClass, branch, pageable)` — same `(:param IS NULL OR ...)` JPQL-guard
  pattern as `SubmissionRepository.search`.

---

## 8. Notable Implementation Details Worth Knowing

### 8.1 `V3__extend_submissions.sql` — CHECK constraint ordering

PostgreSQL enforces a `CHECK` constraint on **every row-level `UPDATE`, not just at commit**. The old
3-state constraint had to be **dropped before** the data remap (`NEW`→`PENDING_CONSULTATION`,
`COMPLETED`→`GRADUATED`), not after — remapping a row to a value the *old* constraint didn't allow would
otherwise fail mid-migration. This was actually proven against real rows in this project's dev database
(the naive ordering failed with a real constraint-violation error, rolled back cleanly since Flyway runs
each migration in one transaction, then was fixed) before landing on the correct order. Worth remembering
for any future status-like enum migration.

### 8.2 `seatsRegistered` / estimated revenue — always computed, never stored

Both `Course.seatsRegistered` (via `CourseResponse`) and the dashboard's `estimatedRevenueThisMonth` are
computed at read time from `Submission` rows, specifically to avoid a second source of truth that could
drift. This was a deliberate two-phase rollout: D1 built `Course` without these fields at all (they
couldn't be computed yet, `Submission.courseId` didn't exist), then D2 wired them up once it did — rather
than adding a placeholder `0` that could be mistaken for real data.

### 8.3 Estimated revenue and pass rate are not real data

Neither figure reflects actual transactions or exam results — this system has no payment or exam-tracking
concept. `estimatedRevenueThisMonth` is a projection (course price × registered-seat count); `passRatePercent`
is manually entered by an admin. Both are labeled as such in Javadoc; don't mistake either for ground truth
when reasoning about the data model.

---

## 9. Testing

Full suite: `./gradlew clean build` (or `./gradlew test`) — **88 tests**, all passing as of Plan 2 D6.

| Class | Layer |
|---|---|
| `BackendApplicationTests` | Full context load, real Postgres |
| `HealthControllerTest`, `SubmissionControllerTest`, `AdminSubmissionControllerTest`, `AuthControllerTest`, `DashboardControllerTest`, `CourseControllerTest`, `AdminCourseControllerTest` | `@WebMvcTest` slices, mocked services, `@MockitoBean` |
| `GlobalExceptionHandlerTest` | Exception → response shape mapping |
| `SecurityIntegrationTest` | Full `@SpringBootTest`, real filter chain, real Postgres — login + per-request auth flows |
| `SubmissionApiIntegrationTest` | Full `@SpringBootTest` + `MockMvc`, **H2** (`test` profile) — real service/repository/Hibernate stack for the Submission API surface, proven hermetic (passes with local Postgres stopped) |
| `SubmissionServiceTest`, `DashboardServiceTest`, `CourseServiceTest`, `CourseMapperTest` | Mockito unit tests |

H2 was chosen over PostgreSQL Testcontainers for `SubmissionApiIntegrationTest` because this dev machine
has no Docker — documented tradeoff (H2 isn't a perfect PostgreSQL dialect match; revisit with
Testcontainers if Docker becomes available) in that test class's own Javadoc.

---

## 10. Docker & Deployment

- **`backend/Dockerfile`** (Phase 20) — multi-stage: `gradle:8.11.1-jdk21` build stage → `eclipse-temurin:21-jre-alpine`
  runtime stage, non-root user, `EXPOSE 8080`.
- **`docker-compose.yml`** (repo root, Phase 21) — `backend` + `postgres:16` services for local Docker-based
  development; `DB_URL` uses the Compose service name (`postgres`), not `localhost`.
- **Production**: deployed to Render (`https://backed-website-register.onrender.com`), backed by Neon
  PostgreSQL. `SPRING_PROFILES_ACTIVE=prod` set on Render. Full walkthrough:
  [`docs/deployment/render-backend-deployment.md`](deployment/render-backend-deployment.md).

---

## 11. How to Run / Verify

Full commands in [`CLAUDE.md`](../CLAUDE.md)'s "Commands" section. In short: `cd backend`, set
`JAVA_HOME`/`PATH` to the portable JDK if no system JDK 21 is available, ensure PostgreSQL is reachable
(local: `tools/pgsql`; **must be UTF8-encoded** — see `CLAUDE.md`'s "Local PostgreSQL" section for why this
matters, discovered when `V5`'s Vietnamese seed data hit a WIN1252-encoded local cluster), then
`./gradlew bootRun` (or `./gradlew clean build` to build and test).

---

## 12. Not Yet Implemented

- **Token revocation** — an issued JWT is valid until it expires; no server-side session/blacklist.
  Accepted tradeoff for this app's size (Phase 16 decision).
- **Real payment/exam tracking** — revenue and pass-rate are estimates/admin-entered, by design (see
  [Section 8.3](#83-estimated-revenue-and-pass-rate-are-not-real-data)) — not a gap to "fix," a deliberate
  scope boundary from `plan-2-full-redesign-driveup.md`.
- **"Giáo viên & Xe" (teachers/vehicles) as real entities** — `Course.teacherName` is a plain string;
  explicitly out of scope per the plan.
- **PostgreSQL Testcontainers** for `SubmissionApiIntegrationTest` — currently H2, would need Docker on the
  dev machine.
- No rate limiting, request logging/auditing beyond SLF4J error logs.
- Roadmap Phases 25–26 (Production Security Review, Final Architecture Review) not yet done.
