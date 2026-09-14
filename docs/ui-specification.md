# Angular Frontend (`clientUI/`) — Technical Specification

**Status as of this document:** Phases 11–15 of the roadmap are implemented (Angular Setup, API Models +
Service, Public User Form, Admin Dashboard UI, Submission Detail UI). Authentication (Phase 16/17) is
**not yet implemented** — see [Not Yet Implemented](#not-yet-implemented) at the end.

This document describes the actual current implementation under `clientUI/src/`, cross-checked against the
real source files (not the roadmap's prose, which may describe a different or future state). For the
phased plan and rationale behind deviations, see `java-spring-boot-angular-project-prompts.md` and
`CHECKLIST.md` at the repo root.

---

## 1. Tech Stack

Sourced from `clientUI/package.json`.

| Package | Version |
|---|---|
| Angular (`@angular/core`, `common`, `forms`, `router`, `platform-browser*`, `compiler`) | ^19.2.0 |
| `@angular/animations` | ^19.2.25 |
| `@angular/cdk` | ^19.2.19 |
| `@angular/material` | ^19.2.19 |
| `rxjs` | ~7.8.0 |
| `zone.js` | ~0.15.0 |
| `typescript` | ~5.7.2 |
| `@angular/cli` / `@angular-devkit/build-angular` (dev) | ^19.2.27 |
| Testing: `jasmine-core`, `karma`, `karma-chrome-launcher`, `karma-jasmine`, `karma-jasmine-html-reporter`, `karma-coverage` | ~5.6.0 / ~6.4.0 / ~3.2.0 / ~5.1.0 / ~2.1.0 / ~2.2.0 |

Notes:
- Angular CLI was pinned to v19 (not "latest") because the dev machine's Node 22.14.0 doesn't satisfy the
  newest CLI's Node requirement — still a fully modern standalone-component setup.
- All components are **standalone** (no `NgModule`s anywhere in the app besides what Angular Material's
  own modules provide) — each component's `@Component` decorator lists its own `imports` array.
- Build style: SCSS, routing enabled, `--skip-git` used at scaffold time (this repo already has its own git
  history).
- Angular Material theme: prebuilt `indigo-pink.css`, loaded via `angular.json`'s global `styles` array
  (not a custom theme file). Global font is Roboto, loaded from Google Fonts via `<link>` tags in
  `index.html` (not build-time inlined — `optimization.fonts: false` was set in the production build
  config due to this environment's network/TLS constraints when fetching fonts at build time).

---

## 2. App Structure

### 2.1 Routing

Defined in `src/app/app.routes.ts`, registered via `provideRouter(routes)` in `src/app/app.config.ts`. No
route guards exist yet (`core/guards/` is present but empty).

| Path | Component | Notes |
|---|---|---|
| `''` (empty) | — | Redirects to `/form` (`pathMatch: 'full'`) |
| `/form` | `InformationFormComponent` | Public submission form |
| `/admin/dashboard` | `DashboardComponent` | Admin dashboard (summary + list) |
| `/admin/submissions/:id` | `SubmissionDetailComponent` | Admin submission detail/status update |

There is no wildcard (`**`) "not found" route, and no route guarding `/admin/**` — any client can navigate
directly to the admin routes today (no auth exists yet; see [Not Yet Implemented](#not-yet-implemented)).

`AppComponent` (`src/app/app.component.ts`) is a bare shell: template is just `<router-outlet>`, no nav
bar/header/layout chrome exists.

### 2.2 Folder Layout

```
clientUI/src/
├── app/
│   ├── admin/
│   │   ├── dashboard/                 DashboardComponent (ts/html/scss/spec)
│   │   └── submission-detail/         SubmissionDetailComponent (ts/html/scss/spec)
│   ├── core/
│   │   ├── guards/                    empty (.gitkeep) — reserved for future auth guards
│   │   ├── interceptors/              empty (.gitkeep) — reserved for future auth interceptor
│   │   └── services/
│   │       └── submission.service.ts  all HTTP calls
│   ├── models/
│   │   ├── submission.model.ts        Submission, CreateSubmissionRequest, UpdateSubmissionStatusRequest, SubmissionStatus
│   │   ├── page-response.model.ts     PageResponse<T>
│   │   └── dashboard-summary.model.ts DashboardSummary
│   ├── public/
│   │   └── information-form/          InformationFormComponent (ts/html/scss/spec)
│   ├── shared/                        empty (.gitkeep) — no shared components built yet
│   ├── app.component.ts/.html/.scss/.spec.ts
│   ├── app.config.ts                  providers: router, HttpClient, async animations
│   └── app.routes.ts
├── environments/
│   ├── environment.ts                 production config (apiBaseUrl)
│   └── environment.development.ts     dev config (apiBaseUrl), swapped in via fileReplacements
├── index.html
├── main.ts
└── styles.scss                        global styles (html/body height, Roboto font-family)
```

This matches the `core/{services,interceptors,guards}`, `models/`, `public/`, `admin/{...}`, `shared/`
structure named in the roadmap.

---

## 3. Components

| Component | Route | Selector | Purpose |
|---|---|---|---|
| `AppComponent` | (root shell) | `app-root` | Renders `<router-outlet>` only; no nav/layout chrome |
| `InformationFormComponent` | `/form` | `app-information-form` | Public reactive form to create a new submission |
| `DashboardComponent` | `/admin/dashboard` | `app-dashboard` | Summary cards + paginated/searchable/filterable submissions table |
| `SubmissionDetailComponent` | `/admin/submissions/:id` | `app-submission-detail` | Loads and displays one submission; lets admin change its status |

None of the routed components declare `@Input()`/`@Output()` — each is a top-level routed page with no
parent passing data via bindings; `SubmissionDetailComponent` gets its identifier from the `:id` route
param instead.

### 3.1 `InformationFormComponent` (`src/app/public/information-form/`)

Public, unauthenticated page rendering a single `mat-card` containing a `FormBuilder`-built `FormGroup`
with four controls, laid out in a centered single column capped at `560px` (full-width submit button below
`600px`).

| Control | Validators | Backend field mirrored |
|---|---|---|
| `fullName` | `required`, `maxLength(200)` | `CreateSubmissionRequest.fullName` (`@NotBlank @Size(max=200)`) |
| `email` | `email`, `maxLength(255)` (not required) | `CreateSubmissionRequest.email` (`@Email @Size(max=255)`, optional) |
| `phone` | `maxLength(30)` | `CreateSubmissionRequest.phone` (`@Size(max=30)`, optional) |
| `message` | `maxLength(2000)` | `CreateSubmissionRequest.message` (`@Size(max=2000)`, optional) |

Behavior:
- `onSubmit()` guards re-entrant submission with a `submitting` boolean (also used to `[disabled]` the
  submit button and swap in a `mat-progress-spinner`).
- Optional fields are trimmed and sent as `undefined` (omitted), not empty strings, when blank.
- Calls `SubmissionService.createSubmission()`. On success: resets the form, shows a success
  `MatSnackBar`. On error: shows the backend's `{message}` (from `GlobalExceptionHandler`'s error shape) via
  snack bar, falling back to a generic message if the body doesn't match that shape.
- No navigation on success/failure — user stays on `/form`.

### 3.2 `DashboardComponent` (`src/app/admin/dashboard/`)

Admin page with two independently-loading sections:

1. **Summary cards** (5 `mat-card` tiles: Total, New, In Progress, Completed, Submitted Today), bound to
   `SubmissionService.getDashboardSummary()`, own `summaryLoading` flag and own error handling (a snack bar
   only — cards render nothing while `null`).
2. **Submissions table** (`mat-table`, columns: `fullName`, `email`, `phone`, `status`, `createdAt`,
   `action`) fed by a plain `Submission[]` array (not `MatTableDataSource`, since paging/filtering/sorting
   are all server-driven), with:
   - `MatPaginator` (`pageIndex`, `pageSize`, `[length]=totalElements`, page sizes `[10, 20, 50]`); every
     page change re-calls `listSubmissions()`.
   - A search `FormControl` piped through a `Subject<string>` with `debounceTime(300ms)` +
     `distinctUntilChanged()` before triggering a reload; resets `pageIndex` to 0.
   - A status `mat-select` (`ALL | NEW | IN_PROGRESS | COMPLETED`) that reloads immediately (no debounce)
     on change, also resetting `pageIndex` to 0; `ALL` omits the `status` query param entirely.
   - Loading spinners shown separately for the summary section and the table region; an explicit "No
     submissions found." empty state when a loaded page has zero rows.
   - Per-row "View" button (`viewSubmission()`) navigates to `/admin/submissions/:id` via `Router`.

Responsive: summary cards `flex-wrap` to stack below 600px; table sits in an `overflow-x: auto` container
with a `min-width` (horizontal scroll rather than column collapse); filters stack vertically below 600px.

### 3.3 `SubmissionDetailComponent` (`src/app/admin/submission-detail/`)

Reads `:id` from `ActivatedRoute.snapshot.paramMap` in `ngOnInit`. An unparseable/missing `id` is treated
identically to a backend 404 (`notFound = true`).

States rendered (mutually exclusive, `@if`/`@else if` chain):

| State | Condition | UI |
|---|---|---|
| Loading | `loading` | `mat-spinner` |
| Not found | `notFound` (missing/unparseable id, or backend 404) | "Submission not found." + Back to Dashboard button |
| Load error | `loadError` (any other fetch failure) | "Something went wrong loading this submission." + Back to Dashboard button, plus an error snack bar |
| Loaded | `submission !== null` | Two-column label/value detail grid + status editor |

Loaded detail grid fields: Full Name, Email, Phone, Message, Status, Created At, Updated At (`null`
email/phone/message rendered as `—`; timestamps formatted with Angular's `date: 'medium'` pipe). No
Company/Position fields (see [field-set deviation](#4-modelsinterfaces) below).

Status editor: a `mat-select` (`statusControl`, options `NEW | IN_PROGRESS | COMPLETED`) initialized to the
loaded submission's current status on every successful load. "Update Status" button:
- Disabled via `isUpdateDisabled()` while no submission is loaded, while a save is in flight, or when the
  selected value equals the submission's current status (avoids a no-op PATCH).
- Calls `SubmissionService.updateStatus(id, statusControl.value)`; on success replaces the displayed
  `submission` (including its refreshed `updatedAt`) with the response and shows a success snack bar; on
  failure shows an error snack bar and re-enables the button.

"Back to Dashboard" button (shown in not-found/error states, and always under the loaded detail card)
navigates to `/admin/dashboard` via `Router`.

Responsive: the two-column `detail-grid` collapses to one column, and the status/update-button row stacks
vertically, below 600px.

---

## 4. Services

Only one service exists: `SubmissionService` (`src/app/core/services/submission.service.ts`,
`providedIn: 'root'`, uses `inject()`). It is the sole place `HttpClient` is used in the app — no component
calls `HttpClient` directly.

| Method | HTTP | Endpoint | Request type | Response type |
|---|---|---|---|---|
| `createSubmission(request)` | `POST` | `${apiBaseUrl}/api/submissions` | `CreateSubmissionRequest` | `Observable<Submission>` |
| `listSubmissions(page?, size?, search?, status?)` | `GET` | `${apiBaseUrl}/api/admin/submissions` | query params `page`, `size`, `search`, `status` (each omitted, not sent blank, if undefined/blank) | `Observable<PageResponse<Submission>>` |
| `getSubmission(id)` | `GET` | `${apiBaseUrl}/api/admin/submissions/{id}` | — | `Observable<Submission>` |
| `updateStatus(id, status)` | `PATCH` | `${apiBaseUrl}/api/admin/submissions/{id}/status` | `{ status }` (`UpdateSubmissionStatusRequest` shape) | `Observable<Submission>` |
| `getDashboardSummary()` | `GET` | `${apiBaseUrl}/api/admin/dashboard/summary` | — | `Observable<DashboardSummary>` |

These map 1:1 to the backend's real controllers/routes as of this writing:
- `SubmissionController` → `POST /api/submissions`
- `AdminSubmissionController` → `GET /api/admin/submissions`, `GET /api/admin/submissions/{id}`,
  `PATCH /api/admin/submissions/{id}/status`
- `DashboardController` → `GET /api/admin/dashboard/summary`

The base URL is built once per resource group from `environment.apiBaseUrl` (no `localhost` hardcoded
anywhere in the service or components).

---

## 5. Models/Interfaces

All under `src/app/models/`, and all documented in-source as mirroring specific backend DTOs.

| TypeScript type | File | Mirrors (backend) | Fields |
|---|---|---|---|
| `SubmissionStatus` (union type) | `submission.model.ts` | `com.register.backend.enums.SubmissionStatus` | `'NEW' \| 'IN_PROGRESS' \| 'COMPLETED'` |
| `Submission` | `submission.model.ts` | `SubmissionResponse` | `id: number; fullName: string; email: string \| null; phone: string \| null; message: string \| null; status: SubmissionStatus; createdAt: string; updatedAt: string` |
| `CreateSubmissionRequest` | `submission.model.ts` | `CreateSubmissionRequest` (request DTO) | `fullName: string; email?: string; phone?: string; message?: string` |
| `UpdateSubmissionStatusRequest` | `submission.model.ts` | `UpdateSubmissionStatusRequest` (request DTO) | `status: SubmissionStatus` |
| `PageResponse<T>` | `page-response.model.ts` | `PageResponse<T>` | `content: T[]; page: number; size: number; totalElements: number; totalPages: number` |
| `DashboardSummary` | `dashboard-summary.model.ts` | `DashboardSummaryResponse` | `total: number; new: number; inProgress: number; completed: number; submittedToday: number` |

Verified against the live backend DTO source (`backend/src/main/java/com/register/backend/dto/`):
- `CreateSubmissionRequest.java` has exactly `fullName` (`@NotBlank @Size(max=200)`), `email`
  (`@Email @Size(max=255)`, no `@NotBlank`/`@NotNull` — optional), `phone` (`@Size(max=30)`), `message`
  (`@Size(max=2000)`) — **no `company`/`position` fields** (an earlier-phase deviation from the original
  roadmap, carried through consistently into the frontend model, form, table, and detail page).
- `SubmissionResponse.java` fields match `Submission` exactly, including `createdAt`/`updatedAt` typed as
  `LocalDateTime` server-side, serialized by Jackson as ISO-8601-ish strings and kept as `string` (not
  parsed into `Date`) on the client.
- `DashboardSummaryResponse.java`'s `newCount` component is annotated `@JsonProperty("new")`, so the wire
  key is `new` — the `DashboardSummary` interface uses `new` as the literal property name to match (valid
  TypeScript; `summary.new` also compiles under this project's strict `tsconfig`).
- `PageResponse<T>.java` fields match `PageResponse<T>` exactly.

Timestamp/date handling: no dates are ever parsed client-side into JS `Date` objects; they're kept as
opaque strings and only formatted for display via Angular's `date` pipe (`date: 'medium'`) in the detail
and dashboard-table templates.

---

## 6. Interceptors, Guards, and Environment Config

- **Interceptors:** none registered. `core/interceptors/` exists as an empty directory (placeholder for a
  future auth interceptor, per the roadmap's later phases).
- **Guards:** none registered. `core/guards/` exists as an empty directory (no guard currently protects
  `/admin/**`); `app.routes.ts` applies no `canActivate` to any route.
- **Environment config:** `src/environments/environment.ts` (production) and
  `environment.development.ts` (development, swapped in via `angular.json`'s `fileReplacements` for the
  `development` build configuration). Both currently set:
  ```ts
  { production: <bool>, apiBaseUrl: 'http://localhost:8080' }
  ```
  `environment.apiBaseUrl` is the single source of the backend's base URL, consumed only by
  `SubmissionService`. No production backend is deployed yet, so both environments point at the same local
  URL for now — the production file is deliberately left with a comment flagging it needs updating at
  deploy time (a later roadmap phase, "Phase 24 — Deploy Angular to Vercel").
- **CORS (backend side, relevant to the frontend's ability to call the API):** `backend`'s `CorsConfig`
  allows origins from `app.cors.allowed-origins` (env var `ALLOWED_ORIGINS`, default
  `http://localhost:4200` — the default Angular dev-server port, i.e. `ng serve`), methods `GET, POST,
  PATCH, OPTIONS`, headers `Content-Type, Authorization`. No `allowCredentials`, no wildcard origin.
- **App-level providers** (`src/app/app.config.ts`): `provideZoneChangeDetection({ eventCoalescing: true
  })`, `provideRouter(routes)`, `provideHttpClient()`, `provideAnimationsAsync()` (Angular Material's async
  animations loader — needed for Material components like `mat-select`/`mat-snack-bar` to animate).

---

## 7. How to Run

See `clientUI/README.md` (standard Angular CLI generated instructions: `ng serve`, `ng build`, `ng test`)
and the root `CLAUDE.md` for how this fits into the wider project (backend must be running separately on
`http://localhost:8080` — see `CLAUDE.md`'s Commands section — for any of the three pages to successfully
load or submit data; the public form and admin pages will otherwise show HTTP error snack bars). No proxy
config file (`proxy.conf.json`) exists — the frontend talks to the backend directly cross-origin, relying
on the backend's CORS configuration described above rather than a dev-server proxy.

---

## Not Yet Implemented

- **No authentication/login UI.** `/admin/dashboard` and `/admin/submissions/:id` are reachable by anyone
  who knows/navigates to the URL — there is no login page, no auth guard, and no HTTP interceptor attaching
  a token. This corresponds to roadmap Phases 16 (backend JWT auth) and 17 (Angular authentication UI),
  both still unchecked in `CHECKLIST.md`.
- **No shared/layout components.** `src/app/shared/` is empty (just a `.gitkeep`) — no shared nav bar,
  header, or reusable UI pieces exist yet; each page is a fully self-contained standalone component.
- **No wildcard/404 route** in `app.routes.ts` for unmatched client-side paths.
- **No client-side sorting UI** on the dashboard table — the backend's default `createdAt DESC` ordering is
  used as-is; no `MatSort` is wired up.
