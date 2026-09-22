# Backend Technical Specification

**Scope:** This document describes the **current, actual implementation** of the Spring Boot backend at
`backend/` in this repository, as of the completion of **Phase 27 — SePay Payment Integration**, which
itself builds on **Plan 2 — Full DriveUp domain adoption** (D1–D6), roadmap Phases 1–23 (backend MVP
through JWT auth, Gradle migration, backend/integration testing, Docker, Docker Compose, Neon/Flyway, and a
live Render deployment), Phase 24 (Angular deployed to Vercel), Phase 25 (Production Security Review — rate
limiting, fail-fast prod secrets) and Phase 26 (Final Architecture Review — read-only, no code changes). It
is derived directly from the source code under `backend/src/main/java/com/register/backend/`, not from the
roadmap document or `README.md`, which may describe future or partially-stale plans.

For the full original phased roadmap, see
[`java-spring-boot-angular-project-prompts.md`](../java-spring-boot-angular-project-prompts.md). For the
DriveUp UI/UX redesign that Plan 2 implements, see
[`docs/planning/plan-2-full-redesign-driveup.md`](planning/plan-2-full-redesign-driveup.md). For the SePay
payment integration's operational/runbook details, see
[`docs/deployment/sepay-payment-workflow.md`](deployment/sepay-payment-workflow.md). For day-to-day working
rules and local environment setup, see [`CLAUDE.md`](../CLAUDE.md) and [`CHECKLIST.md`](../CHECKLIST.md).

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
| `entity/` | JPA entities mapped to database tables (`Submission`, `AdminUser`, `Course`, `DashboardSettings`, `Payment`). |
| `enums/` | Shared enumerations used by entities/DTOs (`SubmissionStatus`, `LicenseClass`, `CourseAvailabilityStatus`, `PaymentStatus`). |
| `exception/` | Custom exceptions, the global error response shape, and the centralized `@RestControllerAdvice` handler. |
| `mapper/` | Manual entity ↔ DTO mapping classes (no MapStruct/ModelMapper — intentionally simple, hand-written). Uniformly stateless **except** `PaymentMapper` (Phase 27), which takes four `@Value`-injected SePay/bank config properties in its constructor to build a VietQR image URL — the one mapper in the codebase that isn't a pure `@Component` with no injected state. |
| `repository/` | Spring Data JPA repository interfaces (derived queries, JPQL `@Query`s, and one native SQL projection query). |
| `security/` | JWT admin authentication: `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`. Also `RateLimitingFilter` (Phase 25) — a per-client-IP rate limiter for the two fully public endpoints (`POST /api/submissions`, `POST /api/auth/login`). |
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

### 3.6 `Payment` (`entity/Payment.java`)

Table: `payments`. Introduced in **Phase 27** for the SePay VietQR payment integration — not part of the
original roadmap or Plan 2.

| Field | Java Type | Column | Nullable | Length / Type | Bean Validation | Notes |
|---|---|---|---|---|---|---|
| `id` | `Long` | `id` | — | — | — | `@Id`, `@GeneratedValue(strategy = IDENTITY)` |
| `submissionId` | `Long` | `submission_id` | `NOT NULL` | `BIGINT`, FK → `submissions(id)` | `@NotNull` | Plain FK id field, not a JPA relationship — same style as `Submission.courseId` → `Course`; no navigation from `Payment` back to a loaded `Submission` entity is needed anywhere |
| `amount` | `BigDecimal` | `amount` | `NOT NULL` | `NUMERIC(12,2)` | `@NotNull` | Snapshotted from `Course.price` at payment-creation time, so a later price edit never retroactively changes an already-generated QR/amount |
| `status` | `PaymentStatus` | `status` | `NOT NULL` | `VARCHAR(20)`, `@Enumerated(EnumType.STRING)`, DB `CHECK (status IN ('PENDING','PAID','CANCELLED'))` | `@NotNull` | Starts `PENDING` on creation |
| `sepayTransactionId` | `String` | `sepay_transaction_id` | nullable | `VARCHAR(100)`, DB `UNIQUE` | none | Set once a webhook marks the payment `PAID`; the DB unique constraint is a race-safety net behind the service layer's own duplicate-webhook pre-check |
| `paidAt` | `LocalDateTime` | `paid_at` | nullable | `TIMESTAMP(6)` | none | Set when the webhook marks the payment `PAID` |
| `createdAt` | `LocalDateTime` | `created_at` | `NOT NULL`, `updatable = false` | — | — | Set via `@PrePersist` |
| `updatedAt` | `LocalDateTime` | `updated_at` | `NOT NULL` | — | — | Set on insert and refreshed via `@PreUpdate` on every update |

Deliberately **decoupled** from `Submission.status` — the admin still manually advances a submission's
status; a payment is shown alongside it as informational context, not auto-linked to it. One submission can
have multiple `Payment` rows over time (e.g. a stale `PENDING` one superseded by a freshly generated one) —
there is no unique constraint tying a submission to a single payment row, only an index
(`idx_payments_submission_id`) for lookup performance. There is deliberately no `EXPIRED` status: this app
has no scheduler/background job, and a stale `PENDING` payment can simply be superseded by generating a
fresh one rather than needing to be aged out automatically.

### 3.7 `PaymentStatus` (`enums/PaymentStatus.java`)

```
PENDING, PAID, CANCELLED
```

Used by `Payment.status` and `PaymentResponse.status`. See [3.6](#36-payment-entitypaymentjava) for why
there's no `EXPIRED` state and why this is deliberately decoupled from `SubmissionStatus`.

---

## 4. REST API

All endpoints are served under the embedded Tomcat server. `server.port` reads `${PORT:8080}` — `8080`
locally/Docker Compose, whatever Render assigns in production. Controller groups: public (`/api/health`,
`/api/submissions`, `/api/courses`, `/api/auth/login`, `/api/webhooks/sepay`), admin (`/api/admin/**`,
JWT-protected — including the Phase 27 `/api/admin/submissions/{id}/payment` endpoints). `POST
/api/submissions` and `POST /api/auth/login` are additionally rate-limited per client IP (Phase 25 — see
[Section 6.3](#63-security-security-package)); `POST /api/webhooks/sepay` is public but separately
authenticated via a shared-secret header rather than Spring Security (see [4.13](#413-post-apiwebhookssepay)).

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
| `estimatedRevenueThisMonth` | Sum of `Course.price` × count, for submissions created since the start of the current calendar month with a registered-seat status and a non-null `courseId`. **An estimate, not real payment data** — deliberately still true after Phase 27: this calculation is entirely independent of the new `Payment` table (see [3.6](#36-payment-entitypaymentjava)); nothing in `DashboardService` reads `Payment` rows. |
| `settings` | The current `DashboardSettings` row, verbatim. |

### 4.11 Admin payment endpoints (`AdminPaymentController`, all `ROLE_ADMIN`)

New in **Phase 27**. Not part of the original roadmap or Plan 2.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/admin/submissions/{submissionId}/payment` | Creates a new `PENDING` payment for the submission's tuition fee (snapshotting `Course.price` as `Payment.amount`), or returns the existing `PENDING` one if already created — **idempotent**, calling it twice in a row does not create a duplicate. `201 Created`. `404` if the submission doesn't exist, has no associated `courseId`, or that course doesn't exist. |
| `GET` | `/api/admin/submissions/{submissionId}/payment` | Retrieves the most recently created payment for the submission (any status). `404` if no payment has ever been created for that submission — the frontend treats this as the normal "no payment requested yet" empty state, not an error. |

### 4.12 `PaymentResponse` shape

Used by both `AdminPaymentController` endpoints.

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | |
| `submissionId` | `Long` | |
| `amount` | `BigDecimal` | Snapshotted `Course.price` at payment-creation time |
| `status` | `PaymentStatus` (`"PENDING"`/`"PAID"`/`"CANCELLED"`) | |
| `paymentCode` | `String` | Derived, not persisted — `"DUP" + zero-padded payment id` (e.g. `DUP000042`), embedded in the transfer content so a webhook payload can be matched back to this payment |
| `qrImageUrl` | `String` | Derived, not persisted — a `https://img.vietqr.io/image/...` URL built by `PaymentMapper` from the `app.sepay.bank-code`/`bank-account-number`/`qr-template` config plus the amount, `paymentCode` (as `addInfo`), and `app.sepay.account-holder-name` (as `accountName`), all URL-encoded |
| `paidAt` | `LocalDateTime` (nullable) | Set once a webhook marks the payment `PAID` |
| `createdAt` / `updatedAt` | `LocalDateTime` | |

### 4.13 `POST /api/webhooks/sepay`

**Controller:** `SepayWebhookController` → `PaymentService.handleSepayWebhook()`. New in **Phase 27**.
Public route (not under `/api/admin/**`), left `permitAll()` by `SecurityConfig`'s existing catch-all rule
— see [Section 6.3](#63-security-security-package) for why this endpoint authenticates itself rather than
going through Spring Security/JWT.

**Request body:** `SepayWebhookRequest` (`id`, `gateway`, `transactionDate`, `accountNumber`, `content`,
`transferType`, `transferAmount`, `referenceCode`) — best-effort field names based on commonly documented
SePay webhook shape, not independently verified against live SePay docs; no Jakarta Validation annotations
on this DTO, since it's an external, unverified sender and the service layer decides what to do with a
partial payload rather than the controller 400ing it.

**Behavior:**
1. The controller compares the `Authorization` header against `"Apikey " + app.sepay.webhook-secret` via
   `MessageDigest.isEqual` (constant-time comparison) — `401 Unauthorized` if missing/mismatched, otherwise
   delegates to `PaymentService.handleSepayWebhook()` and always responds `200 OK`.
2. The service silently ignores (logs and returns, never throws) any payload that: isn't an inbound
   transfer (`transferType != "in"`); has already been processed (`sepayTransactionId` already recorded —
   idempotency, guarded both by an app-level `existsBySepayTransactionId` pre-check and the DB's own unique
   constraint as a race-safety net); has no `content` a `DUP<id>` payment code can be extracted from
   (case-insensitive regex, tolerant of the noisy surrounding bank transfer-content text); references an
   unknown payment id; or references a payment that's no longer `PENDING`.
3. If the reported `transferAmount` is below **90%** of the payment's expected `amount`, the payment is
   left `PENDING` for manual admin review rather than auto-marked paid — a floor against a trivial real bank
   transfer (e.g. 1,000 VND) carrying a guessed/observed sequential `paymentCode` being accepted as full
   payment of an unrelated, much larger tuition amount. A transfer at or above that floor (including an
   amount that doesn't exactly match, e.g. a bank-fee-driven shortfall) is still accepted, with a mismatch
   simply logged — "bank transfer is the source of truth" for minor discrepancies.
4. Otherwise the matching payment is marked `PAID`, `paidAt` is set, and `sepayTransactionId` is recorded.

**Response:** `200 OK` (valid, processed or silently-ignored payload) or `401 Unauthorized` (bad/missing
auth header).

### 4.14 Endpoint summary table

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
| `POST`/`GET` | `/api/admin/submissions/{id}/payment` | `ROLE_ADMIN` | `201`/`200` |
| `POST` | `/api/webhooks/sepay` | None (shared-secret header, verified manually — see [4.13](#413-post-apiwebhookssepay)) | `200` |

`/swagger-ui.html`, `/v3/api-docs`, `/actuator/health`/`/actuator/info` also remain public.

---

## 5. Global Error Handling

**Unchanged since Phase 16** — see `exception/GlobalExceptionHandler.java`, `ErrorResponse.java`. Shape:
`{status, message, errors, timestamp, path}` (`errors` omitted unless present). Every new endpoint in
Plan 2 (Course, Dashboard overview/settings) and Phase 27 (`AdminPaymentController`) funnels through the
same handler — `ResourceNotFoundException` → `404` (e.g. "Course not found with id: {id}", "No payment
found for submission id: {id}"), Bean Validation failures → `400` with field errors, same as `Submission`.
No new exception types were needed for Course/Dashboard/Payment.

Two responses in the same `{status, message, ..., timestamp, path}` `ErrorResponse` shape are written
**outside** `GlobalExceptionHandler` (neither is thrown as a Java exception that reaches it): `429 Too Many
Requests` from `RateLimitingFilter` (Phase 25, a servlet filter that runs before Spring MVC's dispatcher —
see [6.3](#63-security-security-package)), and the `401 Unauthorized` from `SepayWebhookController` on a
bad/missing webhook auth header (Phase 27 — that one is a plain `ResponseEntity`, not even the
`ErrorResponse` shape, since it's a single boolean check with no field-level detail to report).

---

## 6. Cross-Cutting Configuration

### 6.1 CORS (`config/CorsConfig.java`)

Unchanged since Phase 10/16 — `app.cors.allowed-origins` (`ALLOWED_ORIGINS` env var), no wildcard, methods
`GET, POST, PATCH, OPTIONS`, headers `Content-Type, Authorization`.

### 6.2 OpenAPI / Swagger (`config/OpenApiConfig.java`)

Unchanged — auto-generated spec at `/v3/api-docs`, UI at `/swagger-ui.html`, both public.

### 6.3 Security (`security/` package)

Core JWT flow **unchanged since Phase 16** — stateless JWT, `SecurityConfig` protects `/api/admin/**` with
`hasRole("ADMIN")`, everything else `permitAll()`. The `/api/admin/courses/**`, `/api/admin/dashboard/**`,
and (Phase 27) `/api/admin/submissions/{id}/payment` routes all fall under the existing `/api/admin/**` rule
automatically — no rule changes were needed for either Plan 2 or Phase 27. `/api/courses` and
`/api/webhooks/sepay` (both public) fall under the existing catch-all `permitAll()`. See the Phase 16
login/per-request flow description in this doc's git history, or `CLAUDE.md`, for the full JWT walkthrough
— not repeated here since that part hasn't changed.

**Phase 25 added `RateLimitingFilter`**, a plain `OncePerRequestFilter` (not a `@Component` — instantiated
directly inside `SecurityConfig.securityFilterChain()` and registered via `addFilterBefore`, ahead of
`JwtAuthenticationFilter`, which itself runs ahead of Spring Security's default username/password filter).
It applies an in-memory, per-client-IP, fixed 1-minute-window request cap to exactly two fully public,
unauthenticated endpoints that would otherwise have zero abuse protection:

| Endpoint | Default limit | Env var |
|---|---|---|
| `POST /api/submissions` | 5/minute/IP | `RATE_LIMIT_SUBMISSIONS_PER_MINUTE` |
| `POST /api/auth/login` | 10/minute/IP | `RATE_LIMIT_LOGIN_ATTEMPTS_PER_MINUTE` |

Every other request (including all of `/api/admin/**`, which is already behind JWT) passes through
untouched. The client IP is taken from the first entry of `X-Forwarded-For` when present (Render sits
behind a reverse proxy), falling back to `HttpServletRequest.getRemoteAddr()` for local dev. Exceeding the
limit short-circuits the filter chain with `429 Too Many Requests` in the standard `ErrorResponse` shape
(see [Section 5](#5-global-error-handling)); tracking entries are opportunistically swept out at most once
every 5 minutes so the in-memory map doesn't grow unbounded over a long-running instance's lifetime. This is
a deliberately simple fixed-window algorithm (allows up to 2× the limit right at a window boundary), an
accepted tradeoff for a small public app's realistic threat model — see `RateLimitingFilter`'s own Javadoc.

**Phase 27's `POST /api/webhooks/sepay` is deliberately *not* protected by Spring Security or
`RateLimitingFilter` at all** — it's called by SePay's server, not a browser or the admin SPA, so neither
the JWT/`ROLE_ADMIN` model nor a per-client-IP limiter (a single trusted server-to-server caller) fits.
Instead, `SepayWebhookController` verifies the request itself, entirely outside the Spring Security filter
chain: it compares the incoming `Authorization` header against `"Apikey " + app.sepay.webhook-secret`
(`SEPAY_WEBHOOK_SECRET` env var) using `MessageDigest.isEqual` for a constant-time comparison, returning a
bare `401 Unauthorized` on any mismatch/absence before ever calling into `PaymentService`. The exact header
name/scheme is a best-effort guess at SePay's convention, not independently verified against a live SePay
dashboard.

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
| `V6__add_payments_table.sql` | `payments` table (Phase 27) — FK to `submissions`, `status` `CHECK` constraint, and a `UNIQUE` constraint on `sepay_transaction_id` (nullable, so multiple `PENDING` rows with no transaction ID yet are still allowed) |

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

**Phase 25 fail-fast secrets**: `application.yml`'s `${JWT_SECRET:dev-only-...}` and
`${ADMIN_PASSWORD:dev-only-...}` bindings exist purely for local-dev convenience — both defaults are
plaintext-visible in this repo's git history, so `application-prod.yml` re-binds `app.jwt.secret` and
`app.admin.password` with **no fallback value** (`${JWT_SECRET}` / `${ADMIN_PASSWORD}`), so Spring fails to
start with a loud startup error under `SPRING_PROFILES_ACTIVE=prod` if either env var is left unset, rather
than silently running with a known-weak credential. **Phase 27 extends the same fail-fast pattern** to
`app.sepay.webhook-secret` (`${SEPAY_WEBHOOK_SECRET}`, no fallback) — for the same reason: anyone who read
`application.yml`'s dev-only default out of git history could otherwise forge `POST /api/webhooks/sepay`
calls in production and mark arbitrary payments `PAID`. The other four `app.sepay.*` properties
(bank-account-number/bank-code/account-holder-name/qr-template) are **not** given the fail-fast treatment —
an unset one just produces a broken/empty VietQR image URL, not a security exposure, so `application.yml`'s
plain env-var-with-empty-default bindings are left as-is for those.

### 6.7 Environment variables

| Variable | Default (local dev only) | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres | Datasource |
| `ALLOWED_ORIGINS` | `http://localhost:4200` | CORS |
| `JWT_SECRET` | dev-only placeholder | JWT signing (≥32 bytes) — **no fallback in `prod`, see 6.6** |
| `JWT_EXPIRATION_MS` | `3600000` (1h) | Token lifetime |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / dev-only placeholder | Seeded admin account — password **no fallback in `prod`, see 6.6** |
| `PORT` | `8080` | Listen port — Render assigns this dynamically |
| `DASHBOARD_UPCOMING_COURSES_LIMIT` | `4` | Rows in the overview's upcoming-courses list |
| `RATE_LIMIT_SUBMISSIONS_PER_MINUTE` | `5` | Phase 25 — `POST /api/submissions` cap per client IP per minute |
| `RATE_LIMIT_LOGIN_ATTEMPTS_PER_MINUTE` | `10` | Phase 25 — `POST /api/auth/login` cap per client IP per minute |
| `SEPAY_WEBHOOK_SECRET` | dev-only placeholder | Phase 27 — shared secret `SepayWebhookController` expects in the `Authorization: Apikey <secret>` header — **no fallback in `prod`, see 6.6** |
| `SEPAY_BANK_ACCOUNT_NUMBER` | empty | Phase 27 — beneficiary bank account number, used to build the VietQR image URL |
| `SEPAY_BANK_CODE` | empty | Phase 27 — beneficiary bank's VietQR bank code |
| `SEPAY_ACCOUNT_HOLDER_NAME` | empty | Phase 27 — beneficiary account holder's display name shown on the QR |
| `SEPAY_QR_TEMPLATE` | `compact2` | Phase 27 — VietQR image template name |

**Always override the non-`PORT`/non-limit/non-template ones outside local development** — see `README.md`
and [`docs/deployment/sepay-payment-workflow.md`](deployment/sepay-payment-workflow.md) for the SePay-specific
setup.

---

## 7. Mapping & Service Layer Notes

- `SubmissionMapper`, `CourseMapper`, `PaymentMapper` (`mapper/`) — plain `@Component`s, hand-written
  `toEntity`/`toResponse` (and `CourseMapper.applyUpdate` for the admin update endpoint). No library-based
  mapping anywhere. `PaymentMapper` is the one mapper that isn't purely stateless — see the
  [package-layout table](#2-layered-architecture--package-layout) — its constructor takes four
  `@Value`-injected `app.sepay.*` config properties (bank account/code/holder name/QR template) so it can
  build a VietQR image URL and derive a `paymentCode` inside `toResponse()`, both of which are computed, not
  persisted (see [3.6](#36-payment-entitypaymentjava)/[4.12](#412-paymentresponse-shape)).
- **Five** service classes: `SubmissionService`, `DashboardService`, `AuthService`, `CourseService` (D1),
  `PaymentService` (Phase 27) — all constructor injection, `@Transactional(readOnly = true)` on reads,
  `@Transactional` on writes.
- `CourseService.REGISTERED_STATUSES` (`CONFIRMED`/`IN_PROGRESS`/`GRADUATED`) is `public static final` and
  reused as-is by `DashboardService`'s revenue calculation — one source of truth for "what counts as a
  registered seat," not duplicated. `PaymentService` does **not** consult this — it's entirely independent
  of `SubmissionStatus`, by the same "decoupled" design as `Payment` itself.
- `SubmissionRepository` — see [4.5](#45-admin-submission-endpoints-adminsubmissioncontroller-all-role_admin)
  for `search`; also `countByCourseIdAndStatusIn` (a course's live `seatsRegistered`),
  `findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull` (revenue calc),
  `countRegistrationsByMonthSince` (native SQL, month-grouped chart data).
- `CourseRepository.search(licenseClass, branch, pageable)` — same `(:param IS NULL OR ...)` JPQL-guard
  pattern as `SubmissionRepository.search`.
- `PaymentRepository` (Phase 27) — `findBySubmissionIdAndStatus` (the idempotent pending-payment lookup),
  `findFirstBySubmissionIdOrderByCreatedAtDesc` (the "most recent payment" lookup for the `GET` endpoint),
  `existsBySepayTransactionId` (the webhook's app-level duplicate-delivery pre-check).

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

Neither figure reflects actual transactions or exam results. `estimatedRevenueThisMonth` is a projection
(course price × registered-seat count) computed from `Submission` rows, entirely independent of the
`Payment` table introduced in Phase 27 (see [8.4](#84-payment-is-deliberately-decoupled-from-submission-and-the-dashboard));
`passRatePercent` is manually entered by an admin, since this system still has no exam-tracking concept at
all. Both are labeled as such in Javadoc; don't mistake either for ground truth when reasoning about the
data model. (This system *does* now have a real payment concept as of Phase 27 — see below — but the
dashboard's revenue figure was never updated to use it; it remains a pre-Phase-27 estimate.)

### 8.4 `Payment` is deliberately decoupled from `Submission` and the dashboard

Phase 27's `Payment` entity intentionally does **not** feed back into `Submission.status` or
`DashboardService`'s figures. An admin still manually advances a submission through its
`PENDING_CONSULTATION → CONFIRMED → IN_PROGRESS → GRADUATED` lifecycle regardless of payment state; a
payment is surfaced on the admin submission-detail page as informational context only. This keeps the
payment feature additive and low-risk (nothing about the existing submission/course/dashboard behavior
changes), at the cost of the admin dashboard's `estimatedRevenueThisMonth` remaining an estimate rather than
switching to real `Payment.status = PAID` data — a possible future enhancement, not done here.

### 8.5 SePay webhook idempotency and the payment-code amount floor

`PaymentService.handleSepayWebhook()` is designed to be safely called multiple times with the same payload
(SePay, like most webhook senders, may redeliver): it never throws for a malformed/unrecognized/duplicate
payload (logs and returns instead), always resulting in the controller responding `200 OK` so SePay doesn't
retry indefinitely. Duplicate detection is two-layered — an app-level `existsBySepayTransactionId` check
before doing any work, backed by the database's own `UNIQUE` constraint on `sepay_transaction_id` as a
race-safety net (caught as `DataIntegrityViolationException` and treated as "already processed" if two
webhook deliveries for the same transaction somehow race past the app-level check).

The `paymentCode` embedded in a payment's VietQR `addInfo` field (`"DUP" + zero-padded id`, e.g.
`DUP000042`) is a small, guessable, sequential value visible on the QR shown to the student. Without a
floor on the webhook's reported `transferAmount`, anyone who knew the receiving bank account could send a
trivial real transfer (e.g. 1,000 VND) with a guessed/observed code and have `PaymentService` mark an
unrelated, much larger tuition payment `PAID`. `PaymentService.MINIMUM_AMOUNT_RATIO` (90%) guards against
this: a transfer below 90% of the expected amount leaves the payment `PENDING` for manual admin review
instead of auto-completing it, while a transfer at or above that floor (even if not an exact match, e.g. a
bank-fee shortfall) is still accepted per the "bank transfer is the source of truth" design — this was a
real issue found and fixed during Phase 27's independent review pass (see `CHECKLIST.md`'s Phase 27 log
entry), not part of the original implementation.

---

## 9. Testing

Full suite: `./gradlew clean build` (or `./gradlew test`) — **113 tests**, all passing as of Phase 27 (up
from 88 as of Plan 2 D6; Phase 25 added `RateLimitingFilterTest` plus rate-limiting coverage inside
`SecurityIntegrationTest`, bringing the pre-Phase-27 count to 93; Phase 27 then added 20 more: service unit
tests, a `@WebMvcTest` controller test, and a full-context webhook integration test).

| Class | Layer |
|---|---|
| `BackendApplicationTests` | Full context load, real Postgres |
| `HealthControllerTest`, `SubmissionControllerTest`, `AdminSubmissionControllerTest`, `AuthControllerTest`, `DashboardControllerTest`, `CourseControllerTest`, `AdminCourseControllerTest`, `AdminPaymentControllerTest` (Phase 27) | `@WebMvcTest` slices, mocked services, `@MockitoBean` |
| `GlobalExceptionHandlerTest` | Exception → response shape mapping |
| `SecurityIntegrationTest` | Full `@SpringBootTest`, real filter chain, real Postgres — login + per-request auth flows, including Phase 25's rate-limiting-header/429 coverage |
| `RateLimitingFilterTest` (Phase 25) | Direct unit-level test of `RateLimitingFilter`'s window/limit logic |
| `SubmissionApiIntegrationTest` | Full `@SpringBootTest` + `MockMvc`, **H2** (`test` profile) — real service/repository/Hibernate stack for the Submission API surface, proven hermetic (passes with local Postgres stopped) |
| `SepayWebhookIntegrationTest` (Phase 27) | Full `@SpringBootTest` + `MockMvc`, **H2** (`test` profile) — real end-to-end webhook flow: 401 on bad/missing auth header, 200-and-DB-flips-to-`PAID` on a valid payload, idempotent no-op on a replayed payload |
| `SubmissionServiceTest`, `DashboardServiceTest`, `CourseServiceTest`, `CourseMapperTest`, `PaymentServiceTest` (Phase 27) | Mockito unit tests |

H2 was chosen over PostgreSQL Testcontainers for the full-context integration tests (`SubmissionApiIntegrationTest`,
`SepayWebhookIntegrationTest`) because this dev machine has no Docker — documented tradeoff (H2 isn't a
perfect PostgreSQL dialect match; revisit with Testcontainers if Docker becomes available) in
`SubmissionApiIntegrationTest`'s own Javadoc. The `test` profile's `application-test.yml` also raises the
rate-limit env-var-bound properties (`submissions-per-minute`/`login-attempts-per-minute`) far above the
app's real defaults, so `RateLimitingFilter` — a real, shared singleton bean for the whole life of the test
Spring context — doesn't start rejecting legitimate repeated test logins/submissions with `429` partway
through a test class; `RateLimitingFilterTest` exercises the actual limiting behavior directly, unaffected
by that override.

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
- **Real exam-result tracking** — `passRatePercent`/`examCount` remain admin-entered, by design (see
  [Section 8.3](#83-estimated-revenue-and-pass-rate-are-not-real-data)) — not a gap to "fix," a deliberate
  scope boundary from `plan-2-full-redesign-driveup.md`. (Tuition **payment** tracking, by contrast, does
  now exist as of Phase 27's `Payment` entity — see [3.6](#36-payment-entitypaymentjava) — but the dashboard's
  `estimatedRevenueThisMonth` still doesn't consume it; see [8.4](#84-payment-is-deliberately-decoupled-from-submission-and-the-dashboard).)
- **"Giáo viên & Xe" (teachers/vehicles) as real entities** — `Course.teacherName` is a plain string;
  explicitly out of scope per the plan.
- **PostgreSQL Testcontainers** for the H2-based full-context integration tests
  (`SubmissionApiIntegrationTest`, `SepayWebhookIntegrationTest`) — would need Docker on the dev machine.
- **Payment expiry/aging-out** — `PaymentStatus` has no `EXPIRED` state and there's no scheduler/background
  job to age out stale `PENDING` payments; a fresh payment can simply be generated instead (Phase 27
  decision, see [3.6](#36-payment-entitypaymentjava)).
- Request logging/auditing beyond SLF4J error logs (rate limiting itself **is** now implemented — Phase 25's
  `RateLimitingFilter`, see [6.3](#63-security-security-package) — this bullet is about audit trails, not
  abuse protection).
- SePay webhook field names, auth-header scheme, and VietQR bank-code format are best-effort guesses,
  explicitly flagged as unverified against a live SePay dashboard (see [4.13](#413-post-apiwebhookssepay) and
  `docs/deployment/sepay-payment-workflow.md`) — confirm/adjust before relying on the real integration in
  production.
