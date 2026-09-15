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
- [x] **Phase 15 — Submission Detail UI**
  - Log (2026-09-09): Implemented the `/admin/submissions/:id` page in the previously-scaffolded
    `SubmissionDetailComponent` (`admin/submission-detail/`). Switched the `:id` read from the constructor
    to `ngOnInit` (still `route.snapshot.paramMap.get('id')` — no need to react to the same instance being
    reused for a different `:id`, so `snapshot` remains sufficient); an unparseable/missing `id` is treated
    as the same not-found state as a backend 404. `SubmissionService.getSubmission(id)` is called on init
    behind a `loading` flag (`mat-spinner`); the HTTP error handler narrows on `error instanceof
    HttpErrorResponse && error.status === 404` to set a distinct `notFound` flag rendering an explicit "Submission
    not found." message + Back to Dashboard button in the page body (not just a snackbar), separate from a
    generic `loadError` state (any other failure) which also shows the same fallback card plus an error
    snackbar via the same `extractErrorMessage(error, fallback)` pattern used in
    `DashboardComponent`/`InformationFormComponent`. Once loaded, the page renders a two-column
    label/value grid (Full Name, Email, Phone, Message, Status, Created At, Updated At — Company/Position
    omitted, see deviation below), null `email`/`phone`/`message` rendered as `'—'` matching the dashboard
    table's existing pattern, `createdAt`/`updatedAt` formatted via the `date: 'medium'` pipe. A `mat-select`
    status dropdown (`statusControl`, `NEW`/`IN_PROGRESS`/`COMPLETED`) is initialized to the loaded
    submission's status on every successful load; "Update Status" calls
    `SubmissionService.updateStatus(id, statusControl.value)`, disabled via `isUpdateDisabled()` while a
    save is in flight, while no submission is loaded, or when the selected value equals the submission's
    current status (avoids a no-op PATCH). On success the displayed `submission` (including its refreshed
    `updatedAt`) is replaced with the response and a success snackbar shown; on failure an error snackbar is
    shown via the same `extractErrorMessage` helper and the button re-enables. "Back to Dashboard" (shown in
    the not-found/error states and permanently under the loaded detail card) navigates via
    `Router.navigate(['/admin/dashboard'])`. All HTTP logic stays inside the existing `SubmissionService` —
    the component only calls it, never `HttpClient` directly. Responsive layout: the two-column
    `detail-grid` (label/value) collapses to a single column below 600px, and the status
    dropdown/update-button row stacks vertically at the same breakpoint, matching the
    Phase 13/14 breakpoint convention. Updated `submission-detail.component.spec.ts` to add
    `HttpClientTestingModule`, `NoopAnimationsModule`, and `provideRouter([])` to the `TestBed`
    configuration (component now transitively injects `HttpClient` via `SubmissionService` and `Router`,
    and uses Material components needing the animations module) — same pattern as the Phase 13/14 spec
    fixes; the existing mocked `ActivatedRoute` (`{ snapshot: { paramMap: convertToParamMap({ id: '1' }) } }`)
    was kept as-is. Verified: `ng build` succeeds (0 errors; same pre-existing bundle-budget warning
    pattern as Phases 13/14, now 770.05 kB vs. the 500 kB budget — not addressed here, out of this phase's
    scope) and `ng test --watch=false --browsers=ChromeHeadless` passes 6/6 (unchanged count — no new spec
    files added, per the phase's minimal-test-touch instruction).
  - **Deviation (2026-09-09):** displayed fields are Full Name/Email/Phone/Message/Status/Created
    At/Updated At — no Company/Position fields — inheriting the Phase 4 deviation (`company`/`position`
    removed from the backend entity/DTOs entirely), not a new decision made in this phase; the roadmap's
    original Phase 15 prompt still lists Company/Position among the fields to display.

### Out-of-roadmap work — Visual restyle (post-Phase 15)

- (2026-09-14) Restyled the three existing pages (`/form`, `/admin/dashboard`,
  `/admin/submissions/:id`) and added a shared site header — a styling-only pass, not a numbered
  roadmap phase, requested directly (landscaping-business-inspired earthy/green design language). No
  component TypeScript logic, service calls, routing, or validators changed — only templates
  (`.html`), styles (`.scss`), `index.html`, and `angular.json`.
  - **Design system**: new CSS custom properties in `src/styles.scss` (`:root`) — primary
    `#2f6f4e` / dark `#1f4d36` / light tint `#8fbc94`, accent terracotta `#d98e4a` / dark
    `#b8703a`, sand background `#f7f5f0`, white surfaces, warm-dark text `#2b2620`, muted text
    `#6b6459`, plus shared `.status-badge` pill styles (`status-new`/`status-in-progress`/
    `status-completed`) reused by both the dashboard table and the detail page.
  - **Typography**: Poppins (600/700, headings) and Inter (400/500/600, body) loaded via Google
    Fonts `<link>` tags in `src/index.html` (replacing the bare Roboto link), wired as the
    Material theme's `brand-family`/`plain-family` in `styles.scss`.
  - **Material theme**: replaced the `@angular/material/prebuilt-themes/indigo-pink.css` import
    (removed from both the `build` and `test` `styles` arrays in `angular.json`) with a custom
    M3 theme via `@include mat.theme((color: (primary: mat.$green-palette, tertiary:
    mat.$orange-palette, theme-type: light), typography: (...), density: 0))` on `html` in
    `styles.scss` — the exact pattern Angular Material 19's own `ng add` "custom theme" schematic
    generates (checked `node_modules/@angular/material/schematics/ng-add/theming/create-custom-theme.js`
    directly rather than assuming M2 `mat.define-light-theme` syntax, since that's deprecated in
    this version). No `mat.core()`/`mat.all-component-themes()` needed — v19 components already
    read the `--mat-sys-*` system tokens this mixin emits. Verified in the built CSS:
    `--mat-sys-primary: #026e00` (green), `--mat-sys-tertiary: #964900` (terracotta-brown), and
    `Poppins`/`Inter` present in the typescale custom properties.
  - **Shared header**: new nav bar in `app.component.html`/`.scss` (`AppComponent` gained
    `RouterLink`/`RouterLinkActive`/`MatIconModule`/`MatToolbarModule` imports only — no other TS
    logic) — left brand (leaf `mat-icon` + text), right nav links to `/form` and
    `/admin/dashboard` with `routerLinkActive` highlighting, collapsing to a stacked layout below
    600px. `app.component.spec.ts` updated to provide `provideRouter([])` (now required since the
    template uses `routerLink`).
  - **Per-page restyle**: public form gained a gradient hero band above the existing white
    rounded form card (all fields/validators/submit-spinner behavior untouched); admin dashboard's
    5 stat cards gained a left accent bar + circular icon per stat, the submissions table sits in
    a rounded bordered container with row hover, and the status column renders the shared
    `.status-badge` pill (computed via a template expression,
    `'status-' + submission.status.toLowerCase().replace('_','-')` — no new TS method); submission
    detail got the same status badge plus a Material icon per field label in the label/value grid,
    all three load states (loading/not-found/error) restyled with the site's card/shadow language.
    `DashboardComponent`/`SubmissionDetailComponent` each gained a `MatIconModule` import (for
    `<mat-icon>`) — no other TS changes.
  - Verified: `npm run build` succeeds (0 errors; the pre-existing bundle-budget warning grew
    slightly, 723.31 kB vs. the 500 kB budget, from pulling in `MatIconModule`/`MatToolbarModule`
    — not addressed here, budget tuning is out of scope) and `npm test -- --watch=false
    --browsers=ChromeHeadless` passes 6/6 unchanged (only `app.component.spec.ts` needed a fix, for
    the new `routerLink` usage — no spec asserted on markup text that changed). Visual/browser
    rendering was not screenshotted from this environment; only build output and generated CSS
    were inspected to confirm the theme/fonts/tokens compiled as intended.

## Secured MVP (Phases 16–17)

- [x] **Phase 16 — Spring Security (JWT Admin Auth)**
  - Log (2026-09-14): Added `spring-boot-starter-security` and `io.jsonwebtoken:jjwt-api/jjwt-impl/jjwt-jackson:0.12.6` to `pom.xml`. New `AdminUser` entity (`id`, `username` unique, `passwordHash`, `role`,
    `createdAt`, `@PrePersist`-managed timestamp, matching `Submission`'s plain-JPA/no-Lombok style) +
    `AdminUserRepository` (`findByUsername`). New `security/` package: `JwtService` (jjwt 0.12.x encode/decode,
    HS256, secret + expiration bound from `app.jwt.secret`/`app.jwt.expiration-ms` ⇐
    `JWT_SECRET`/`JWT_EXPIRATION_MS` env vars, 1h default), `JwtAuthenticationFilter`
    (`OncePerRequestFilter`, reads `Authorization: Bearer`, sets the `SecurityContext` straight from the
    token's `sub`/`role` claims — no DB lookup per request, so a valid, unexpired signature is trusted as-is;
    a missing/invalid/expired token just leaves the request unauthenticated rather than throwing),
    `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` (write the existing `ErrorResponse` JSON shape
    directly for 401/403, since `@RestControllerAdvice` never sees exceptions thrown inside the security
    filter chain), and `SecurityConfig` (`@EnableWebSecurity`, stateless `SecurityFilterChain`, CSRF disabled,
    `.cors(Customizer.withDefaults())` — verified this correctly reuses the existing `CorsConfig`
    `WebMvcConfigurer` registration via Spring Security's `HandlerMappingIntrospector` auto-detection, no
    separate `CorsConfigurationSource` bean needed — `OPTIONS` preflight permitted globally,
    `/api/admin/**` requires `hasRole("ADMIN")`, everything else `permitAll()`). New `AuthService`
    (`@Transactional(readOnly = true)`, looks up `AdminUserRepository.findByUsername`, verifies via
    `BCryptPasswordEncoder.matches`, throws new `InvalidCredentialsException` — same message for
    unknown-username and wrong-password, to avoid leaking which one failed — on any mismatch, otherwise
    issues a JWT via `JwtService`) + `AuthController` (`POST /api/auth/login`) + `LoginRequest`/`LoginResponse`
    records. `GlobalExceptionHandler` gained an `InvalidCredentialsException` → 401 case, same pattern as the
    existing `ResourceNotFoundException` → 404 one. New `config/AdminUserSeeder` (`ApplicationRunner`,
    idempotent — only inserts when `adminUserRepository.count() == 0` — seeds `app.admin.username`/
    `app.admin.password` ⇐ `ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars, hashed via the same
    `BCryptPasswordEncoder` bean used at login). `application.yml` gained `app.jwt.secret`/
    `app.jwt.expiration-ms`/`app.admin.username`/`app.admin.password`, all following the existing
    env-var-with-dev-only-default pattern.
  - **Design decision — no `UserDetailsService`/`AuthenticationManager` bean:** login is handled directly
    in `AuthService` against `AdminUserRepository`, and per-request auth is handled entirely from the JWT's
    own signed claims in `JwtAuthenticationFilter`. This is simpler for a single-role admin-only app, avoids
    Spring Boot's auto-configured default in-memory user (and its generated-password startup log line) that
    otherwise appears once `spring-boot-starter-security` is on the classpath with no auth mechanism
    configured, and sidesteps the `@WebMvcTest`-pulls-in-a-JPA-repository failure mode the phase's prompt
    flagged as a risk — the filter chain's beans (`JwtService`, the two REST handlers) have no DB dependency
    at all.
  - **`@WebMvcTest` gotcha encountered exactly as warned:** `@WebMvcTest` does **not** automatically include
    arbitrary `@Configuration` classes like `SecurityConfig` in the slice context, so `HealthControllerTest`
    was getting Spring Boot's *default* security auto-configuration (require-auth-for-everything) instead of
    the app's real, more permissive `SecurityConfig` — a false `401` on `GET /api/health` inside the slice
    test only. Fixed with `@AutoConfigureMockMvc(addFilters = false)` on that test (it isn't testing security
    behavior), per the "fix it in the test, don't weaken production config" instruction;
    `/api/health`'s genuine public-with-no-token behavior is verified for real in `SecurityIntegrationTest`
    instead.
  - **Tests:** new `security/SecurityIntegrationTest` — a full `@SpringBootTest` +`@AutoConfigureMockMvc`
    (real filter chain, real seeded admin user, real Postgres) rather than mocked slices, since these
    specifically need to exercise the whole pipeline (`AdminUserSeeder` → real login → real signed token →
    real `JwtAuthenticationFilter`) rather than any one collaborator in isolation. Covers: `/api/health`
    public with no token; login success (200 + token/username/role); login failure for both a wrong password
    and an unknown username (401, same generic message either way); an admin endpoint with no token (401),
    a garbage token (401), an expired token (401, generated in-test with the same configured secret but a
    past expiration), and a valid token (200) — 8 tests total, all passing.
  - **Manual end-to-end verification** (packaged jar run against the live local Postgres): `GET
    /api/health` with no token → 200; `GET /api/admin/dashboard/summary` with no token → 401
    `{"message":"Authentication required"}`; `POST /api/auth/login` with a wrong password → 401
    `{"message":"Invalid username or password"}`; login with the correct seeded credentials → 200 + JWT;
    the same admin endpoint with `Authorization: Bearer <token>` → 200 with real summary data; `POST
    /api/submissions` with no token → 201 (still public, unaffected); a garbage bearer token → 401; CORS
    `OPTIONS` preflight and an authenticated `GET` from `Origin: http://localhost:4200` on the admin
    endpoint both still return the expected `Access-Control-Allow-Origin` header, confirming the security
    filter chain didn't regress Phase 10's CORS behavior. Verified via `mvn clean verify` (19/19 tests
    pass) and the curl sequence above; the test submission and its row were cleaned up afterward.
  - **Seeded dev-only admin credentials** (from `application.yml`'s defaults, override via
    `ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars in any real deployment): username `admin`, password
    `dev-only-ChangeMe123!`.
- [x] **Phase 17 — Angular Authentication**
  - Log (2026-09-14): New `models/auth.model.ts` (`LoginRequest`/`LoginResponse`, mirroring the
    backend's `LoginRequest`/`LoginResponse` records exactly). New `core/services/auth.service.ts`
    (`AuthService`, `providedIn: 'root'`) — `login()` posts to `${apiBaseUrl}/api/auth/login` and,
    on success, persists `{token, username, role}` to `localStorage` under the key `auth`;
    `logout()` clears both the in-memory signal and `localStorage`; `getToken()` reads the current
    token; `isAuthenticated`/`username` are `computed()` signals derived from a private
    `signal<StoredAuth | null>` seeded on construction from `localStorage` (so a page reload stays
    logged in). New `core/interceptors/auth.interceptor.ts` (`authInterceptor`, functional
    `HttpInterceptorFn`, registered via `provideHttpClient(withInterceptors([authInterceptor]))` in
    `app.config.ts`) — attaches `Authorization: Bearer <token>` only to requests whose URL starts
    with `${environment.apiBaseUrl}/api/admin/` (matches `SubmissionService`'s
    `adminSubmissionsUrl`/`dashboardSummaryUrl`, leaves `/api/submissions`, `/api/health`, and
    `/api/auth/login` untouched by construction, not a denylist), and doubles as the app's global
    401 handler for admin requests (`catchError` → on a 401 from an admin URL, calls
    `authService.logout()` and `router.navigate(['/admin/login'])`, then rethrows). New
    `core/guards/auth.guard.ts` (`authGuard`, functional `CanActivateFn`) — returns `true` if
    `authService.isAuthenticated()`, otherwise a `UrlTree` to `/admin/login`; applied via
    `canActivate: [authGuard]` on both `admin/dashboard` and `admin/submissions/:id` in
    `app.routes.ts`, plus a new public `admin/login` route rendering the new `LoginComponent`
    (`admin/login/`, matching the `admin/dashboard`/`admin/submission-detail` folder convention).
    `LoginComponent` follows `InformationFormComponent`'s exact conventions (`FormBuilder` reactive
    form, required-only `username`/`password` validators since the backend only enforces
    `@NotBlank`, `submitting` guard flag, Material card/form-field/spinner/snackbar, same
    `extractErrorMessage` pattern) and navigates to `/admin/dashboard` on success. Nav header
    (`app.component.html`/`.ts`/`.scss`) now reads `AuthService.isAuthenticated()`/`.username()`
    reactively (no page reload) to show a "Login" link when logged out or the current username +
    a "Logout" button when logged in; `onLogout()` calls `authService.logout()` then navigates to
    `/admin/login`. Removed the now-empty `.gitkeep` placeholders in `core/guards/` and
    `core/interceptors/` (same cleanup pattern as Phase 12). `DashboardComponent`/
    `SubmissionDetailComponent` needed **no changes** — their existing `extractErrorMessage`
    helpers already handle any error shape generically, and a 401 specifically is fully intercepted
    and redirected before those components' `error` callbacks even see it in the normal case (a
    401 body still flows through to them too, but by then the redirect is already in flight).
    `app.component.spec.ts` gained `HttpClientTestingModule` (now required since `AppComponent`
    injects `AuthService`, which injects `HttpClient`); `dashboard.component.spec.ts`/
    `submission-detail.component.spec.ts` needed no changes (already had
    `HttpClientTestingModule`/`provideRouter([])`, and the guard isn't exercised when a spec
    constructs the component directly rather than navigating a route). New
    `login.component.spec.ts` added (same minimal "should create" pattern as the other two admin
    specs). Verified: `npm run build` succeeds (0 errors; bundle-budget warning grew slightly to
    729.16 kB vs. the 500 kB budget from the new login page's Material imports — not addressed
    here, out of scope) and `npm test -- --watch=false --browsers=ChromeHeadless` passes 7/7 (6
    existing + 1 new `LoginComponent` spec).
  - **Storage strategy — `localStorage` (chosen over `sessionStorage`/in-memory-only):** the JWT
    and username/role are stored as JSON in `localStorage` (not a cookie — the backend returns the
    token as a JSON body field, so an `httpOnly` cookie approach isn't available without backend
    changes, which are out of scope for this phase). **Tradeoff, as required by the roadmap
    prompt:** `localStorage` (like `sessionStorage`) is readable by any JavaScript running on the
    page's origin, so a successful XSS attack against this Angular app could exfiltrate the token
    — this is strictly worse than an `httpOnly` cookie, which JS can never read at all, at the cost
    of needing backend `Set-Cookie`/CSRF-token plumbing this phase doesn't add. Given the token's
    short lifetime (1h, per Phase 16's `app.jwt.expiration-ms` default) and that this is a small
    internal admin tool with no third-party scripts, no rendering of raw/unescaped HTML from
    user-submitted data (Angular's template binding auto-escapes all interpolated submission
    fields — no `innerHTML` used anywhere in the app), and no other known XSS vector, the residual
    risk was judged acceptable. `localStorage` was picked over `sessionStorage` specifically for
    the UX of surviving a page reload/new tab without forcing a re-login (an admin dashboard is
    plausibly refreshed or reopened during a session); the tradeoff is that a token left in
    `localStorage` also survives browser restarts until it expires or `logout()` runs, whereas
    `sessionStorage` would auto-clear when the tab closes — a marginally smaller exposure window
    for the same XSS risk profile, deemed not worth the reduced convenience for a small
    internal-only tool. An in-memory-only signal (cleared on every reload) was rejected as
    needlessly inconvenient for an admin who reloads the page — same XSS exposure while a session
    is active anyway, since the token still has to live in JS-readable memory to be attached to
    requests.
  - **Deviation:** none — implemented per the roadmap's Phase 17 prompt and this task's brief;
    `DashboardComponent`/`SubmissionDetailComponent` were deliberately left unmodified since the
    interceptor's global 401 handling fully covers the "handle HTTP 401" requirement without
    per-component special-casing (see reasoning above).

### Out-of-roadmap work — Backend build tool: Maven → Gradle (post-Phase 17)

- (2026-09-14) Replaced the backend's build tool, Maven → Gradle — a tooling migration only, requested
  directly, not a numbered roadmap phase. No application code changed; scope was `backend/` only.
  - **New**: `backend/build.gradle` (Groovy DSL, standard Spring Initializr shape) and
    `backend/settings.gradle`, porting every dependency from `pom.xml` 1:1 (Maven `compile` →
    `implementation`, `runtime` → `runtimeOnly`, `test` → `testImplementation`). Plugins:
    `org.springframework.boot` 3.4.1, `io.spring.dependency-management` 1.1.7, Java toolchain 21.
    Gradle Wrapper generated at 8.11.1 (`backend/gradlew`, `gradlew.bat`, `gradle/wrapper/`).
  - **Removed**: `backend/pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, plus stray untracked
    `target/`/`app.log`/`app_out.log` build debris.
  - **Modified**: `backend/.gitignore` — Maven entries (`target/`, `.mvn/wrapper` exception) swapped
    for Gradle equivalents (`.gradle/`, `build/`, `!gradle/wrapper/gradle-wrapper.jar`).
  - **Command mapping** (also reflected in `CLAUDE.md`/`README.md`): `mvn clean verify` →
    `./gradlew clean build`; `mvn test "-Dtest=X"` → `./gradlew test --tests "fully.qualified.X"`;
    `mvn clean package -DskipTests` → `./gradlew clean bootJar -x test`; `mvn spring-boot:run` →
    `./gradlew bootRun`; jar output moved from `target/` to `build/libs/`.
  - **Sandbox-specific network note**: this dev machine sits behind a TLS-inspecting proxy that
    intercepts `github.com`/`objects.githubusercontent.com` (Gradle's distribution download redirects
    through GitHub Releases) but *not* `services.gradle.org` directly or Maven Central — so a
    genuinely fresh `./gradlew` bootstrap can fail here specifically on the one-time Gradle
    distribution download (dependency resolution itself, via `mavenCentral()`, is unaffected). Worked
    around by downloading the distribution zip via `curl --ssl-no-revoke` and pre-seeding
    `~/.gradle/wrapper/dists/gradle-8.11.1-bin/<hash>/gradle-8.11.1-bin.zip` directly. A local copy of
    the Gradle distribution is kept at `tools/gradle-8.11.1` (git-ignored) as a fallback re-seed
    source if this cache is ever cleared on this machine; `tools/apache-maven-3.9.9` is fully
    superseded and no longer needed. Not an issue on a machine without this specific proxy.
  - Verified: `./gradlew clean build` — all 19 backend tests pass, 0 failures. `./gradlew bootJar` +
    `java -jar build/libs/backend-0.0.1-SNAPSHOT.jar` and `./gradlew bootRun` both confirmed serving
    `GET /api/health` → 200. `./gradlew dependencies --configuration runtimeClasspath` confirmed no
    dependency (BCrypt, JJWT ×3, springdoc, Postgres driver) silently dropped vs. the Maven tree.

## Testing (Phases 18–19)

- [x] **Phase 18 — Backend Unit Tests** (JUnit 5 + Mockito, Service layer)
  - Log (2026-09-14): `DashboardServiceTest.getSummaryAssemblesCountsFromRepository` and four pre-existing
    `SubmissionServiceTest` methods (`getSubmissionByIdReturnsMappedResponseWhenSubmissionExists`,
    `getSubmissionByIdThrowsResourceNotFoundExceptionWhenSubmissionDoesNotExist`,
    `updateStatusUpdatesAndReturnsMappedResponseWhenSubmissionExists`,
    `updateStatusThrowsResourceNotFoundExceptionWhenSubmissionDoesNotExist`) already covered
    get/not-found/update-status/dashboard-summary. Added four new `SubmissionServiceTest` methods to close
    the remaining gap: `createSubmissionSavesAndReturnsMappedResponseWhenRequestIsValid` (happy-path
    create — mapper→entity, repository save, mapper→response, return value all asserted),
    `createSubmissionForcesStatusToNewBeforeSaving` (asserts, via an `ArgumentCaptor<Submission>` on the
    `save()` call, that a pre-set non-NEW status on the mapped entity is overwritten to `NEW` before
    persisting), `listSubmissionsReturnsMappedPageResponseWhenSearchAndStatusProvided` (search + status
    both provided; asserts `submissionRepository.search(...)` receives the exact filter values and that
    the returned `PageResponse`'s `content`/`page`/`size`/`totalElements`/`totalPages` correctly reflect a
    mocked `PageImpl`), and `listSubmissionsNormalizesBlankSearchToNullBeforeQuerying` (calls with a
    blank `"   "` search and asserts `submissionRepository.search(null, ...)` is invoked, not
    `search("   ", ...)`). All new tests follow the file's existing Mockito + AssertJ + Arrange/Act/Assert
    style, no Spring context started.
  - **Extension (2026-09-14, by request):** the phase's prompt says "Focus on Service layer," but the user
    explicitly asked for controller-layer unit tests too, so 4 new `@WebMvcTest` classes were added under
    `controller/`: `SubmissionControllerTest` (3 tests — create-success 201, blank-fullName 400,
    invalid-email 400), `AdminSubmissionControllerTest` (6 tests — list with search+status params, list
    with both omitted asserting `null`s are passed through, get-by-id success, get-by-id 404, update-status
    success, update-status 400 on missing `status`), `DashboardControllerTest` (1 test — summary passthrough),
    and `AuthControllerTest` (3 tests — login success, login 401 on bad credentials, login 400 on blank
    username). Each slice mocks its service via `@MockitoBean` (Spring Boot 3.4's replacement for the
    deprecated `@MockBean`) and disables the security filter chain (`@AutoConfigureMockMvc(addFilters =
    false)`, matching the pre-existing `HealthControllerTest` pattern) since authentication/authorization is
    already covered end to end by `SecurityIntegrationTest` — these tests verify controller
    request/response mapping and delegation to the service layer only. Verified: `./gradlew test` — full
    suite (36 tests: `BackendApplicationTests` 1, `AdminSubmissionControllerTest` 6, `AuthControllerTest` 3,
    `DashboardControllerTest` 1, `HealthControllerTest` 1, `SubmissionControllerTest` 3,
    `GlobalExceptionHandlerTest` 4, `SecurityIntegrationTest` 8, `DashboardServiceTest` 1,
    `SubmissionServiceTest` 8) passes, 0 failures, 0 errors.
- [x] **Phase 19 — Integration Tests**
  - Log (2026-09-14): New `integration/SubmissionApiIntegrationTest`
    (`backend/src/test/java/com/register/backend/integration/`, new package) — a full `@SpringBootTest` +
    `@AutoConfigureMockMvc` (real Spring context, real `SubmissionService`/`SubmissionRepository`/
    Hibernate/JSON serialization, not mocked collaborators, unlike the pre-existing `@WebMvcTest` slices)
    covering all 5 endpoints in scope: `POST /api/submissions` (201 + full JSON body on valid input; 400 +
    field errors for blank `fullName`/invalid `email`), `GET /api/admin/submissions` (pagination across 25
    seeded rows — page/size/totalElements/totalPages; search substring match; status filter; combined
    search+status), `GET /api/admin/submissions/{id}` (200 + correct body; 404 for a nonexistent id),
    `PATCH /api/admin/submissions/{id}/status` (200 + updated body on a valid status; 400 on an
    unrecognized status string `"BOGUS"`; 404 for a nonexistent id), and `GET
    /api/admin/dashboard/summary` (200 + counts matching seeded NEW/IN_PROGRESS/COMPLETED data). 12 tests
    total. Admin endpoints get a real bearer token via an actual `POST /api/auth/login` call in
    `@BeforeEach` (seeded `app.admin.username`/`app.admin.password` credentials) rather than disabling the
    security filter chain, per the phase's explicit instruction. `POST /api/submissions` stays
    unauthenticated. Class-level `@Transactional` rolls back each test's seeded data automatically, keeping
    tests isolated from each other; submissions with a specific pre-set status are seeded directly via
    `SubmissionRepository.saveAndFlush(...)` (the create endpoint always forces `NEW`).
  - **Database: H2 vs. PostgreSQL Testcontainers.** This dev machine has no Docker (`docker --version`
    fails to resolve), so Testcontainers — which would give true PostgreSQL dialect parity with production
    — cannot run here. Used H2 in-memory instead: added `com.h2database:h2` as a `testImplementation` in
    `backend/build.gradle`, and a new `backend/src/test/resources/application-test.yml` (activated via
    `@ActiveProfiles("test")`) pointing at `jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1` with
    `ddl-auto: create-drop` for a fresh isolated schema per run. Tradeoff written up in the test class's
    own Javadoc, citing this codebase's own precedent: Phase 6's `lower(bytea) does not exist` bug was a
    PostgreSQL/Hibernate-specific type-inference quirk on a null bind parameter that only ever surfaced
    against real PostgreSQL — an H2-backed suite (this one included) would not have caught it either, since
    H2's type inference differs from PostgreSQL's. Recommendation, not implemented here: reconsider
    PostgreSQL Testcontainers for this suite once Docker is available in the dev/CI environment.
  - **Verification (requirement 11 — no dependency on the production/dev database):** stopped local
    PostgreSQL (`tools\pgsql\bin\pg_ctl.exe -D tools\pgdata stop`), confirmed down via `pg_isready` (exit
    code 2, "no response"), then ran `./gradlew test --tests
    "com.register.backend.integration.SubmissionApiIntegrationTest"` — all 12 tests passed with Postgres
    down, proving the new suite is fully hermetic. Restarted PostgreSQL
    (`tools\pgsql\bin\pg_ctl.exe -D tools\pgdata -l tools\pg.log -o "-p 5432" start`), confirmed up via
    `pg_isready`, then ran the full suite: `./gradlew clean test` — **48/48 tests pass, 0 failures, 0
    errors** (up from 36/36 before this phase; the pre-existing Postgres-dependent
    `BackendApplicationTests`/`SecurityIntegrationTest` were left untouched and still pass against real
    PostgreSQL, as required).

## Deployment (Phases 20–24)

- [x] **Phase 20 — Dockerize Spring Boot**
  - Log (2026-09-15): New `backend/Dockerfile` — multi-stage build. Build stage: official
    `gradle:8.11.1-jdk21` image (pinned to match `gradle/wrapper/gradle-wrapper.properties`, avoids
    depending on the wrapper's own distribution download inside the build), runs `gradle bootJar
    --no-daemon -x test` then copies the boot jar (excluding the `-plain.jar` Gradle also produces, via
    `cp $(ls build/libs/*.jar | grep -v plain) app.jar` — avoids hardcoding the version string in the
    Dockerfile). Runtime stage: `eclipse-temurin:21-jre-alpine` (JRE only, no JDK/Gradle in the final
    image), runs as a new non-root `spring` user, `EXPOSE 8080`, `ENTRYPOINT ["java", "-jar", "app.jar"]`.
    New `backend/.dockerignore` (`build/`, `.gradle/`, `out/`, `bin/`, `.idea/`, `.vscode/`, `*.iml`,
    `.git`, `.gitignore`, `*.log`, `HELP.md`, `README.md`). No `ENV` instructions in the Dockerfile and no
    credentials anywhere in it — `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET`/`ALLOWED_ORIGINS` are
    already read via `${VAR:default}` in `application.yml` (Phases 2/10/16), so they're supplied entirely
    at `docker run` time.
  - **Tests skipped in the image build (`-x test`), by design:** this repo's `@SpringBootTest`s
    (`BackendApplicationTests`, `SecurityIntegrationTest`) hit a real PostgreSQL datasource — not available
    during `docker build` — so running the full suite belongs to CI/`gradlew build`, not image
    construction.
  - Commands:
    ```
    cd backend
    docker build -t register-backend:latest .

    docker run -d --name register-backend -p 8080:8080 \
      -e DB_URL="jdbc:postgresql://<host>:5432/information_db" \
      -e DB_USERNAME="postgres" \
      -e DB_PASSWORD="<password>" \
      -e JWT_SECRET="<32+ byte random secret>" \
      -e ALLOWED_ORIGINS="http://localhost:4200" \
      register-backend:latest
    ```
  - **Verified:** `docker build` — `BUILD SUCCESSFUL`, final image 409 MB (JRE-alpine + jar only, vs.
    ~650MB+ for a JDK-based image). Ran the built image in an isolated Docker network against a disposable
    `postgres:16` container with all 5 required env vars set — logs show Hibernate schema creation, admin
    user seeding, and `Started BackendApplication in 7.503 seconds`; `curl
    http://localhost:18080/actuator/health` → `{"status":"UP"}`. Verification containers/network removed
    afterward; only the built `register-backend:latest` image kept locally.
- [x] **Phase 21 — Docker Compose for Local Development**
  - Log (2026-09-15): New root-level `docker-compose.yml` — two services, `postgres` (image `postgres:16`,
    named volume `postgres_data` at `/var/lib/postgresql/data`, `pg_isready` healthcheck referencing the
    container's own `$$POSTGRES_USER`/`$$POSTGRES_DB` env vars) and `backend` (`build: context: ./backend`,
    i.e. the Phase 20 `Dockerfile`; `depends_on: postgres: condition: service_healthy`; `DB_URL:
    jdbc:postgresql://postgres:5432/...` — the Compose **service name**, never `localhost`, per
    requirement 4; `ports: ["8080:8080"]`). All values are `${VAR:-dev-default}` — same "dev-only,
    CHANGE-ME" defaults already established in `application.yml` (Phases 2/10/16), no production
    credentials anywhere in the file. New root `.env.example` documenting the overridable vars
    (`POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`/`JWT_SECRET`/`ALLOWED_ORIGINS`) — `docker compose`
    auto-loads a sibling `.env` file, so a developer copies `.env.example` → `.env` to override without
    touching `docker-compose.yml`. `.gitignore` gained `/.env` so a real override file is never committed.
  - **Docker networking:** Compose puts both services on one auto-created bridge network
    (`website-register_default`); within it, each service is reachable by its service name via Docker's
    embedded DNS (`postgres` resolves to the `register-postgres` container), which is why `DB_URL` uses
    `postgres:5432` rather than `localhost`. Only `backend` publishes a port to the host
    (`8080:8080`) — `postgres` has no `ports:` mapping, so it's reachable only from other containers on
    that network, not from the host or the internet.
  - **Verified:** `docker compose build` succeeded (reused Phase 20's cached image layers). `docker compose
    up -d` — `register-postgres` reported `healthy` before `register-backend` started (proving the
    `depends_on` healthcheck condition works); `curl http://localhost:8080/actuator/health` →
    `{"status":"UP"}`; backend logs show schema creation and admin-user seeding succeeding over the
    `postgres` hostname. **Persistence check:** `docker compose down` (no `-v`) → volume still listed in
    `docker volume ls` → `docker compose up -d` again → logs show **no** re-seed of the admin user,
    confirming data survived. **Full teardown:** `docker compose down -v` removed containers, network, and
    the `postgres_data` volume — confirmed gone afterward.
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
