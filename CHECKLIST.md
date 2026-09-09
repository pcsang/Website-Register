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
- [x] **Phase 8 — Update Submission Status** (`PATCH /api/admin/submissions/{id}/status`)
  - Log (2026-09-07): `UpdateSubmissionStatusRequest` (record, `status` typed as the `SubmissionStatus` enum
    directly + `@NotNull`). `SubmissionService.updateStatus()` (`@Transactional`) looks up via
    `findById`/`orElseThrow(ResourceNotFoundException)` (same pattern as Phase 7), sets the new status, and
    persists via `submissionRepository.saveAndFlush()`, then maps to `SubmissionResponse`. New
    `PATCH /{id}/status` method on the existing `AdminSubmissionController`. Added a
    `GlobalExceptionHandler` case for `HttpMessageNotReadableException` (400) — Jackson rejects an
    unrecognized enum string (e.g. `"BOGUS"`) before validation ever runs, so it needed its own handler
    separate from the existing `MethodArgumentNotValidException` one (which still covers a present-but-null
    `status`). `SubmissionServiceTest` gained `updateStatus` success/not-found cases;
    `GlobalExceptionHandlerTest` gained a case exercising the malformed-body/invalid-enum path end to end via
    MockMvc (judged that this deserialization boundary is better tested at the web layer, since by the time
    a value reaches the service it's already a valid enum). Verified 200/404/400×2 end-to-end against the
    running app and live database.
  - **Bug found and fixed while verifying against a live database (not caught by `mvn verify` — the unit
    tests mock the repository, so they don't exercise real flush timing):** the initial implementation used
    `submissionRepository.save()`, and the response DTO was built immediately after — but Hibernate defers
    the actual flush (and therefore the `@PreUpdate` callback that bumps `updatedAt`) to transaction commit,
    which happens *after* the service method returns. The PATCH response was echoing back a stale
    `updatedAt` (identical to `createdAt`) even though the DB was correctly updated a moment later. Switched
    to `saveAndFlush()` so the flush — and `@PreUpdate` — runs before the response is mapped.
- [x] **Phase 9 — Dashboard Summary** (`GET /api/admin/dashboard/summary`)
  - Log (2026-09-07): New `DashboardSummaryResponse` record (`@JsonProperty("new")` on the `newCount`
    component so the JSON key is `new` while the Java identifier avoids the near-keyword). New
    `DashboardService.getSummary()` (`@Transactional(readOnly = true)`) assembles the response from five
    separate database `COUNT` queries against `SubmissionRepository`: `count()` (total),
    `countByStatus(...)` ×3 (NEW/IN_PROGRESS/COMPLETED, new derived method), and
    `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startOfToday, startOfTomorrow)` (new derived
    method, half-open range) for "submitted today" — "today" computed via
    `LocalDate.now().atStartOfDay()` / `.plusDays(1)`, i.e. server-local-time start-of-day through
    start-of-next-day, matching how `Submission.createdAt` is populated
    (`LocalDateTime.now()` in `@PrePersist`, no time zone stored) rather than assuming UTC. New
    `DashboardController` (`/api/admin/dashboard/summary`) — separate from `AdminSubmissionController`
    since the resource path differs. New `DashboardServiceTest` (Mockito) verifies the five repository
    calls are made and the response is assembled correctly. Verified via `Hibernate: select count(...)`
    log lines (5 queries, all `COUNT`, none loading full rows) and cross-checked the returned totals
    against `psql` `GROUP BY status` / date-range counts on the live database.
  - **Design note (multiple COUNT queries vs. one aggregate query):** five separate simple `COUNT` queries
    were chosen over one grouped/aggregate query. For a single-entity app with an admin dashboard hit
    infrequently (not a hot path), the simplicity and readability of derived Spring Data methods
    (`countByStatus`, `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan`) outweighs the minor
    round-trip savings of a single `GROUP BY` query — and a `GROUP BY status` query wouldn't cover the
    "today" count anyway (different predicate, not part of the status grouping), so it would still need a
    second query, closing most of the gap. Building a single hand-rolled aggregate `@Query` for a handful
    of `COUNT`s over one small table was judged premature optimization with no measured performance
    problem to justify it.

- [x] **Phase 10 — CORS**
  - Log (2026-09-08): New `config/CorsConfig` (`WebMvcConfigurer.addCorsMappings`, applied to `/api/**`)
    — confirmed no Spring Security dependency exists yet, so plain Spring MVC CORS support is sufficient
    and no new dependency was added. Allowed origins bound via `@Value("${app.cors.allowed-origins}")`
    (constructor injection), split on `,` — `application.yml` adds `app.cors.allowed-origins:
    ${ALLOWED_ORIGINS:http://localhost:4200}`, matching the existing `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
    relaxed-binding-with-local-default pattern. Allowed methods restricted to `GET, POST, PATCH, OPTIONS`;
    allowed headers `Content-Type, Authorization` (the latter future-proofed for Phase 16 JWT auth, not
    used yet). No wildcard `*` origin — only the configured explicit origin list is echoed back.
    `allowCredentials` intentionally left unset/default (no cookies/session-based auth planned; a future
    JWT will travel via `Authorization` header, which doesn't need CORS credentials mode) — simplest
    option that satisfies the requirement. Verified via `mvn clean verify` (11/11 tests unaffected) and
    manually against the running app: `OPTIONS` preflight and `GET
    /api/admin/dashboard/summary` with `Origin: http://localhost:4200` both returned
    `Access-Control-Allow-Origin: http://localhost:4200`; the same `GET` with `Origin: http://evil.com`
    was rejected with `403 Invalid CORS request` and no `Access-Control-Allow-Origin` header.

---

## User MVP (Phases 11–13)

- [x] **Phase 11 — Angular Setup**
  - Log (2026-09-07): New Angular project scaffolded at repo-root sibling `clientUI/` (not nested in
    `backend/`), Angular 19 (the CLI's `@angular/cli@latest` requires Node ≥22.22.3/24.15.1/26 which this
    machine's Node 22.14.0 doesn't satisfy — pinned to `@angular/cli@19`, still fully modern/standalone,
    routing + SCSS, `--skip-git` since this repo already has its own git history). Angular Material added
    via `ng add @angular/material` (indigo-pink theme, typography, animations); had to separately
    `npm install @angular/animations` since the schematic didn't pull it in on its own, and manually add
    `provideHttpClient()` + `provideAnimationsAsync()` to `app.config.ts` (the schematic didn't wire
    providers into `app.config.ts` for this CLI version). Required folder structure created under
    `src/app/`: `core/{services,interceptors,guards}`, `models/`, `public/information-form/`,
    `admin/{dashboard,submission-detail}/`, `shared/` — the still-empty directories (`core/*`, `models/`,
    `shared/`) got a `.gitkeep` so they survive being committed later. Three standalone placeholder
    components generated via `ng generate component` (`InformationFormComponent`, `DashboardComponent`,
    `SubmissionDetailComponent`), each rendering only a heading + one-line placeholder text; wired into
    `app.routes.ts` for `/form`, `/admin/dashboard`, `/admin/submissions/:id` (plus a `''` → `/form`
    redirect), with `app.component.html` reduced to a bare `<router-outlet>` (was the generated Angular
    welcome-page boilerplate). `SubmissionDetailComponent` reads the `:id` route param via
    `ActivatedRoute` (kept minimal — just `inject(ActivatedRoute).snapshot.paramMap.get('id')` displayed on
    the placeholder, no data fetching) so routing can be verified end to end even though the phase forbids
    real UI/API logic. `src/environments/environment.ts` / `environment.development.ts` generated via
    `ng generate environments` (wires `fileReplacements` in `angular.json` automatically), both populated
    with `apiBaseUrl: 'http://localhost:8080'` (matching Phase 11's stated backend dev URL; production has
    no deployed backend yet so it points at the same value for now, with a comment noting it needs updating
    at deploy time) — no HTTP service layer built yet, per the phase's explicit scope (that's Phase 12).
    `core/guards/` and `core/interceptors/` left empty (no auth/interceptor logic, per requirement 9).
    Fixed two build-environment issues unrelated to app code: `ng build`'s font-inlining step failed against
    this network's self-signed-cert TLS interception when fetching Google Fonts at build time — disabled
    with `optimization.fonts: false` on the production build config (Google Fonts are still loaded at
    runtime via the existing `<link>` tags in `index.html`; this only turns off build-time CSS inlining of
    the font). Also fixed the CLI-generated `app.component.spec.ts` (asserted on the now-removed welcome-page
    `<h1>`) and `submission-detail.component.spec.ts` (missing `ActivatedRoute` test provider, needed once
    the component started injecting it). Verified: `ng build` (production config) succeeds; `ng test
    --watch=false --browsers=ChromeHeadless` — 6/6 pass; `ng serve` on port 4300 with manual `curl` checks
    that `/form`, `/admin/dashboard`, and `/admin/submissions/42` all return `200` via the Angular dev
    server's SPA history-API fallback (visual rendering not curl-able since this is a client-rendered SPA
    with no SSR, but the served `index.html` + bundle files were confirmed correct and unit/component tests
    cover each placeholder rendering).
  - **Deviation:** none from the phase's functional requirements; the folder name `clientUI` (vs. a more
    generic `frontend`) was an explicit instruction from the requester, not a roadmap deviation — the
    roadmap doesn't name the frontend directory.
- [x] **Phase 12 — Angular API Models + Service**
  - Log (2026-09-08): New TypeScript interfaces under `src/app/models/` grouped by concept —
    `submission.model.ts` (`SubmissionStatus` union type, `Submission`, `CreateSubmissionRequest`,
    `UpdateSubmissionStatusRequest`), `page-response.model.ts` (`PageResponse<T>`),
    `dashboard-summary.model.ts` (`DashboardSummary`, with the `new` field kept as the real wire key per
    the backend's `@JsonProperty("new")` on `DashboardSummaryResponse.newCount` — verified `new` as an
    unquoted interface property name and `summary.new` property access both compile fine under this
    project's strict `tsconfig`). New `SubmissionService` (`src/app/core/services/submission.service.ts`,
    `providedIn: 'root'`, `inject()` style matching the Phase 11 scaffold's convention) wraps all 5
    endpoints: `createSubmission`, `listSubmissions` (page/size/search/status, `HttpParams.set(...)` only
    for params actually provided — blank/undefined `search` and `status` are omitted rather than sent
    empty), `getSubmission`, `updateStatus`, `getDashboardSummary`. API base URL centralized via
    `environment.apiBaseUrl`, no `localhost` hardcoded in the service. Removed the now-redundant
    `.gitkeep` placeholders in `models/` and `core/services/`. No component changes (per the phase's
    explicit scope — UI wiring is Phases 13–15). Verified: `ng build` succeeds; `ng test --watch=false
    --browsers=ChromeHeadless` — 6/6 pass (unchanged, no new component tests needed here); no `any` used
    anywhere in the new code.
- [x] **Phase 13 — Public User Form**
  - Log (2026-09-09): Implemented the `/form` page in the previously-scaffolded
    `InformationFormComponent` (`public/information-form/`). `FormBuilder`-built reactive form with four
    controls (`fullName`, `email`, `phone`, `message`), validators matching the real backend contract
    exactly (see deviation note below): `fullName` `required` + `maxLength(200)`; `email` `email` +
    `maxLength(255)`, no `required`; `phone` `maxLength(30)`; `message` `maxLength(2000)`. Angular Material
    (`mat-card`, `mat-form-field`/`matInput`, `mat-error`, `mat-raised-button`, `mat-progress-spinner`,
    `MatSnackBar`) used throughout; new `@if` control-flow syntax for conditional `mat-error`/spinner
    rendering. `onSubmit()` guards against re-entrant submission via a `submitting` boolean (checked at the
    top of the method and used to `[disabled]` the submit button), trims optional fields and omits them
    entirely (`undefined`, not empty string) when blank before calling
    `SubmissionService.createSubmission()`, resets the form and shows a success snack bar on success, and
    on error extracts the backend's `GlobalExceptionHandler` `{message}` shape (falling back to a generic
    message if the error body doesn't match) and shows it via a snack bar — never fails silently. All HTTP
    logic stays in `SubmissionService` (Phase 12); the component only calls it. Layout is a centered,
    single-column `mat-card` capped at `560px` with a small-viewport media query that stretches the submit
    button full-width below `600px`. Updated `information-form.component.spec.ts` to add
    `HttpClientTestingModule` and `NoopAnimationsModule` to the `TestBed` providers, since the component now
    transitively injects `HttpClient` (via `SubmissionService`) and uses Material components that need the
    animations module present in tests. Verified: `ng build` succeeds (0 errors; only a pre-existing-style
    bundle-budget warning now that Material modules are pulled into this route, 596 KB vs. the 500 KB
    budget — not addressed here since lazy-loading/budget tuning is out of this phase's scope) and `ng test
    --watch=false --browsers=ChromeHeadless` passes 6/6 (unchanged count — no new spec files added, per the
    phase's minimal-test-touch instruction).
  - **Deviation (2026-09-09):** Field set is `fullName`, `email`, `phone`, `message` only — no Company or
    Position fields, and `email` is optional (not `required`) — this is not a new decision made in this
    phase, it's carrying forward the Phase 4 deviation (`company`/`position` removed from the backend
    entirely, `email` made optional) so the client-side contract matches what `CreateSubmissionRequest`
    actually accepts today rather than the roadmap's original Phase 13 field list/validation table.

## Admin MVP (Phases 14–15)

- [x] **Phase 14 — Admin Dashboard UI**
  - Log (2026-09-09): Implemented the `/admin/dashboard` page in the previously-scaffolded
    `DashboardComponent` (`admin/dashboard/`). Five `mat-card` summary tiles (Total, New, In Progress,
    Completed, Submitted Today) bound to `SubmissionService.getDashboardSummary()`, loaded independently of
    the submissions list (separate `summaryLoading` flag and try/catch-equivalent `error` handler, so a
    failure in one request doesn't block the other, per requirement 11). Submissions rendered via
    `MatTable` fed by a plain `Submission[]` (`submissions`) rather than a `MatTableDataSource` — since
    pagination/filtering/sorting are all server-side, `MatTableDataSource`'s built-in client-side
    pagination/sorting/filtering machinery isn't needed and was deliberately not used to avoid fighting the
    backend's own paging. `MatPaginator` wired via its `(page)` event (`onPageChange`) to
    `pageIndex`/`pageSize`, using `PageResponse.totalElements` as `[length]` — every page change calls
    `listSubmissions()` again against the backend (requirement 4). Search input is a `FormControl` piped
    through a `Subject<string>` with `debounceTime(300)` + `distinctUntilChanged()` before triggering a
    reload (requirement 12); the status `mat-select` (`ALL`/`NEW`/`IN_PROGRESS`/`COMPLETED`) reloads
    immediately on change (no debounce needed for a discrete control), and `ALL` omits the `status` param
    entirely per `SubmissionService`'s existing contract. Both search and status changes reset `pageIndex`
    to `0` before reloading, per the phase's explicit requirement. No `MatSort`/client-driven sorting added
    — the backend's default `createdAt DESC` ordering is left as-is (requirement 7). Loading indicators
    (`mat-spinner`) shown separately for the summary section and the table region; an explicit empty-state
    message ("No submissions found.") renders when a loaded page has zero rows. `viewSubmission()` uses
    `Router.navigate(['/admin/submissions', id])` for the per-row View button. All HTTP calls stay inside
    the existing `SubmissionService` — the component only calls it, never `HttpClient` directly. Responsive
    layout: summary cards use `flex-wrap` (stack to full width below 600px), the table sits in an
    `overflow-x: auto` container with a `min-width` so it scrolls horizontally on narrow viewports instead
    of collapsing columns, and the search/status filters stack vertically below 600px. Updated
    `dashboard.component.spec.ts` to add `HttpClientTestingModule`, `NoopAnimationsModule`, and
    `provideRouter([])` to the `TestBed` configuration, since the component now transitively injects
    `HttpClient` (via `SubmissionService`) and `Router`, and uses Material components that need the
    animations module present in tests — same pattern as the Phase 13 fix to
    `information-form.component.spec.ts`. Verified: `ng build` succeeds (0 errors; same pre-existing
    bundle-budget warning pattern as Phase 13, now 764.99 kB vs. the 500 kB budget — not addressed here,
    out of this phase's scope) and `ng test --watch=false --browsers=ChromeHeadless` passes 6/6 (unchanged
    count — no new spec files added, per the phase's minimal-test-touch instruction).
  - **Deviation (2026-09-09):** submission table columns are Full Name/Email/Phone/Status/Created
    At/Action — no Company column — inheriting the Phase 4 deviation (`company` removed from the backend
    entity/DTOs entirely) rather than a new decision made in this phase; the roadmap's original Phase 14
    prompt still lists a Company column that no longer has a corresponding field on `Submission`.
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
