# Implementation Checklist

Tracks progress against the phased plan in
[`java-spring-boot-angular-project-prompts.md`](java-spring-boot-angular-project-prompts.md). One phase =
one roadmap step, implemented and verified in isolation, per that file's "one step at a time" rule.

**How to use this file:** check a box only once a phase is implemented *and* verified (build passes,
manually tested). Add a short **Log** entry under it — date, what was actually built, and any deviation
from the original prompt (renamed field, dropped requirement, extra fix, etc.) so the reasoning isn't lost.
Leave unstarted phases as-is; don't pre-fill notes for work not yet done.

---

## Backend MVP (Phases 1–10)

- [x] **Phase 1 — Spring Boot Project Setup**
  - Log (2026-08-28): Maven project initialized under `backend/`, package structure
    (`config/controller/dto/entity/enums/exception/mapper/repository/security/service`) created, `GET
    /api/health` returns `{"status":"UP"}`. Dev machine had no JDK 21/Maven/winget access — worked around
    with a portable JDK 21 + Maven toolchain (see `CLAUDE.md`).

- [x] **Phase 2 — PostgreSQL Configuration**
  - Log (2026-08-28): `application.yml` datasource wired to `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars
    with local-dev defaults (`information_db` / `postgres` / `postgres`). `ddl-auto: update` chosen —
    reasoning logged in that step's output. Verified against a real local PostgreSQL instance.

- [x] **Phase 3 — Submission Entity**
  - Log (2026-09-03): `Submission` entity + `SubmissionStatus` enum (`NEW`, `IN_PROGRESS`, `COMPLETED`,
    `@Enumerated(STRING)`) + `SubmissionRepository`. Timestamps via `@PrePersist`/`@PreUpdate` (chosen over
    Spring Data auditing — single entity, no auditor needed, see `CLAUDE.md`). Schema verified directly via
    `psql`.

- [x] **Phase 4 — POST /api/submissions**
  - Log (2026-09-03): `CreateSubmissionRequest`, `SubmissionResponse`, `SubmissionMapper`,
    `SubmissionService`, `SubmissionController`. Controller → Service → Repository, `status` forced to
    `NEW` server-side, `201 Created` on success. Verified end-to-end against a live database.
  - **Deviation (2026-09-03, by request):** `company` and `position` fields removed entirely; `email` made
    optional (was required in the original prompt). Entity, DTOs, mapper, and the live DB schema were all
    updated to match.

- [x] **Phase 5 — Global Exception Handling**
  - Log (2026-09-03): `GlobalExceptionHandler` (`@RestControllerAdvice`) handles validation errors (400 +
    field errors), `ResourceNotFoundException` (404), and any other exception (500, generic message,
    logged server-side only). Also added a handler for Spring's `NoResourceFoundException` — without it,
    the catch-all was turning ordinary unmatched-route 404s into false 500s. `@Email` message customized to
    match the spec's exact wording. Verified 400/404/500 shapes against the running app.

- [x] **Phase 6 — Admin Submission List** (`GET /api/admin/submissions` — pagination, search, filter)
  - Log (2026-09-04): `AdminSubmissionController` + `SubmissionService.listSubmissions()` +
    `SubmissionRepository.search()` (single JPQL `@Query` with `(:param IS NULL OR ...)` guards — chosen
    over Specification since there are only two optional filters; simplest maintainable option per the
    phase's requirement 12). `page`/`size`/`sort` bound automatically via a `Pageable` controller
    parameter (`@PageableDefault` for `createdAt DESC`); `spring.data.web.pageable.max-page-size: 100`
    added to `application.yml` to cap oversized `size` requests. New `PageResponse<T>` DTO
    (`content, page, size, totalElements, totalPages`). Filtering/paging execute at the database level
    (verified via the generated SQL in the app log, not just assumed).
  - **Deviation (2026-09-04):** requirement 3 said to search across fullName/email/phone/company, but
    `company` no longer exists on the entity (removed in the Phase 4 deviation above) — searched
    fullName/email/phone only.
  - **Bugs found and fixed while verifying against a live database (not caught by `mvn verify` — the unit
    tests don't exercise this against real Postgres):**
    1. A `null` `:search` bind inside `LOWER(...)` made PostgreSQL fail with `function lower(bytea) does
       not exist` — a known Hibernate/PostgreSQL gotcha for untyped nullable string parameters. Fixed with
       `CAST(:search AS string)` in the JPQL.
    2. An invalid `sort` field (e.g. `?sort=company,asc`) fell through the existing catch-all exception
       handler as a false `500` instead of `400`. Added a `GlobalExceptionHandler` case for
       `InvalidDataAccessApiUsageException` (what Spring Data actually throws here — an initial guess of
       `PropertyReferenceException` was wrong and was corrected after checking the real stack trace).
       Also added a case for `MethodArgumentTypeMismatchException` so an invalid `?status=` value returns
       `400` instead of `500`.

- [x] **Phase 7 — Admin Submission Detail** (`GET /api/admin/submissions/{id}`)
  - Log (2026-09-04): `SubmissionService.getSubmissionById()` (`@Transactional(readOnly = true)`) looks up
    via `submissionRepository.findById()`, `orElseThrow(() -> new ResourceNotFoundException("Submission not
    found with id: " + id))` (same message format already established/tested in
    `GlobalExceptionHandlerTest`), maps to `SubmissionResponse` via the existing `SubmissionMapper`. New
    `GET /{id}` method added to the existing `AdminSubmissionController` — no new controller class, no new
    exception type, no duplicate exception handling (existing `ResourceNotFoundException` → 404 handler
    from Phase 5 fires as-is). New `SubmissionServiceTest` (Mockito, `@ExtendWith(MockitoExtension.class)`)
    covers submission-exists (mapped response returned) and submission-does-not-exist
    (`ResourceNotFoundException` thrown, mapper never invoked). Verified 200/404 end-to-end against the
    running app and live database (`POST` a submission, then `GET` its ID and a nonexistent ID).
- [ ] **Phase 8 — Update Submission Status** (`PATCH /api/admin/submissions/{id}/status`)
- [ ] **Phase 9 — Dashboard Summary** (`GET /api/admin/dashboard/summary`)
- [ ] **Phase 10 — CORS**

---

## User MVP (Phases 11–13)

- [ ] **Phase 11 — Angular Setup**
- [ ] **Phase 12 — Angular API Models + Service**
- [ ] **Phase 13 — Public User Form**

## Admin MVP (Phases 14–15)

- [ ] **Phase 14 — Admin Dashboard UI**
- [ ] **Phase 15 — Submission Detail UI**

## Secured MVP (Phases 16–17)

- [ ] **Phase 16 — Spring Security (JWT Admin Auth)**
- [ ] **Phase 17 — Angular Authentication**

## Testing (Phases 18–19)

- [ ] **Phase 18 — Backend Unit Tests** (JUnit 5 + Mockito, Service layer)
  - Note: `BackendApplicationTests`, `HealthControllerTest`, and `GlobalExceptionHandlerTest` already exist
    from earlier phases, but they don't satisfy this phase's specific scope (mocked-repository unit tests
    for `SubmissionService`/`DashboardService`) — still open.
- [ ] **Phase 19 — Integration Tests**

## Deployment (Phases 20–24)

- [ ] **Phase 20 — Dockerize Spring Boot**
- [ ] **Phase 21 — Docker Compose for Local Development**
- [ ] **Phase 22 — Production Database: Neon**
- [ ] **Phase 23 — Deploy Spring Boot to Render**
- [ ] **Phase 24 — Deploy Angular to Vercel**

## Review (Phases 25–26)

- [ ] **Phase 25 — Production Security Review**
- [ ] **Phase 26 — Final Architecture Review**

---

## Milestones

- [x] **Milestone 1 — Backend MVP core path** (`POST /api/submissions` → Spring Boot → PostgreSQL) —
  reachable and tested via curl/Postman as of Phase 4/5.
- [ ] Milestone 2 — Public MVP (Angular form live)
- [ ] Milestone 3 — Admin MVP (admin can see submitted data)
- [ ] Milestone 4 — Secured MVP (admin login required)
- [ ] Milestone 5 — Internet Deployment
