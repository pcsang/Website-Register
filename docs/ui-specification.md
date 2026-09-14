# Angular Frontend (`clientUI/`) — Technical Specification

**Status as of this document:** Phases 11–17 of the roadmap are implemented (Angular Setup, API Models +
Service, Public User Form, Admin Dashboard UI, Submission Detail UI, and — as of the two most recent
phases — a full admin login flow: `AuthService`, an HTTP interceptor, a route guard, and the `/admin/login`
page). A landscaping-inspired visual redesign (custom Material theme, shared nav header) also landed as an
out-of-roadmap styling pass between Phases 15 and 16. See [Not Yet Implemented](#not-yet-implemented) at
the end for what's still open.

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
- Build style: SCSS, routing enabled.
- **Angular Material theme: custom, not the `indigo-pink` prebuilt.** `src/styles.scss` defines a green/
  earth-toned M3 theme via `@include mat.theme(...)` (primary `mat.$green-palette`, tertiary
  `mat.$orange-palette`) plus a set of CSS custom properties (`--color-primary: #2f6f4e`, `--color-accent:
  #d98e4a`, sand background `#f7f5f0`, etc.) and a shared `.status-badge` pill class reused by the dashboard
  table and detail page. Fonts: **Poppins** (600/700, headings) and **Inter** (400/500/600, body), loaded
  via Google Fonts `<link>` tags in `index.html` — replacing the earlier bare Roboto setup. This was a
  deliberate, out-of-roadmap visual redesign (landscaping-business-inspired), not a numbered phase — see
  `CHECKLIST.md`'s "Out-of-roadmap work — Visual restyle" log entry for full detail.

---

## 2. App Structure

### 2.1 Routing

Defined in `src/app/app.routes.ts`, registered via `provideRouter(routes)` in `src/app/app.config.ts`.

| Path | Component | Guard | Notes |
|---|---|---|---|
| `''` (empty) | — | — | Redirects to `/form` (`pathMatch: 'full'`) |
| `/form` | `InformationFormComponent` | none | Public submission form |
| `/admin/login` | `LoginComponent` | none | Public admin login page |
| `/admin/dashboard` | `DashboardComponent` | **`authGuard`** | Admin dashboard (summary + list) |
| `/admin/submissions/:id` | `SubmissionDetailComponent` | **`authGuard`** | Admin submission detail/status update |

`authGuard` (`core/guards/auth.guard.ts`) redirects an unauthenticated visitor to `/admin/login` (via
`Router.createUrlTree`, not a hard navigation) — see [Section 6](#6-interceptors-guards-and-environment-config).
There is still no wildcard (`**`) "not found" route for unmatched client-side paths.

`AppComponent` (`src/app/app.component.ts`) now renders a real shared site header (brand + nav), not a bare
`<router-outlet>` shell — see [Section 3.5](#35-appcomponent-shared-nav-header).

### 2.2 Folder Layout

```
clientUI/src/
├── app/
│   ├── admin/
│   │   ├── dashboard/                 DashboardComponent (ts/html/scss/spec)
│   │   ├── login/                     LoginComponent (ts/html/scss/spec)
│   │   └── submission-detail/         SubmissionDetailComponent (ts/html/scss/spec)
│   ├── core/
│   │   ├── guards/
│   │   │   └── auth.guard.ts          authGuard (functional CanActivateFn)
│   │   ├── interceptors/
│   │   │   └── auth.interceptor.ts    authInterceptor (functional HttpInterceptorFn)
│   │   └── services/
│   │       ├── auth.service.ts        AuthService (login/logout/auth state)
│   │       └── submission.service.ts  all submission/dashboard HTTP calls
│   ├── models/
│   │   ├── auth.model.ts              LoginRequest, LoginResponse
│   │   ├── submission.model.ts        Submission, CreateSubmissionRequest, UpdateSubmissionStatusRequest, SubmissionStatus
│   │   ├── page-response.model.ts     PageResponse<T>
│   │   └── dashboard-summary.model.ts DashboardSummary
│   ├── public/
│   │   └── information-form/          InformationFormComponent (ts/html/scss/spec)
│   ├── shared/                        still empty (.gitkeep) — no shared components built yet
│   ├── app.component.ts/.html/.scss/.spec.ts   shared site header/nav + <router-outlet>
│   ├── app.config.ts                  providers: router, HttpClient (+authInterceptor), async animations
│   └── app.routes.ts
├── environments/
│   ├── environment.ts                 production config (apiBaseUrl)
│   └── environment.development.ts     dev config (apiBaseUrl), swapped in via fileReplacements
├── index.html                         Poppins/Inter + Material Icons font links
├── main.ts
└── styles.scss                        design tokens (CSS custom properties), custom Material M3 theme
```

This matches the `core/{services,interceptors,guards}`, `models/`, `public/`, `admin/{...}`, `shared/`
structure named in the roadmap.

---

## 3. Components

| Component | Route | Selector | Purpose |
|---|---|---|---|
| `AppComponent` | (root shell) | `app-root` | Shared site header/nav (brand, Form/Admin Dashboard links, Login-or-username+Logout) + `<router-outlet>` |
| `InformationFormComponent` | `/form` | `app-information-form` | Public reactive form to create a new submission |
| `LoginComponent` | `/admin/login` | `app-login` | Admin login form |
| `DashboardComponent` | `/admin/dashboard` | `app-dashboard` | Summary cards + paginated/searchable/filterable submissions table |
| `SubmissionDetailComponent` | `/admin/submissions/:id` | `app-submission-detail` | Loads and displays one submission; lets admin change its status |

None of the routed components declare `@Input()`/`@Output()` — each is a top-level routed page with no
parent passing data via bindings; `SubmissionDetailComponent` gets its identifier from the `:id` route
param instead.

### 3.1 `InformationFormComponent` (`src/app/public/information-form/`)

Public, unauthenticated page. A gradient hero band (part of the visual redesign) sits above a `mat-card`
containing a `FormBuilder`-built `FormGroup` with four controls.

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
- The JWT is **never** sent on this request — `authInterceptor` only attaches it to `/api/admin/**` URLs
  (this one is `/api/submissions`).

### 3.2 `LoginComponent` (`src/app/admin/login/`)

Public admin login page, introduced in Phase 17. Follows `InformationFormComponent`'s exact conventions:
`FormBuilder` reactive form, a `submitting` guard flag, Material card/form-field/spinner/snackbar.

| Control | Validators |
|---|---|
| `username` | `required` |
| `password` | `required` |

Backend enforces only `@NotBlank` on both, so no stricter client-side validation was added.

Behavior:
- `onSubmit()` calls `AuthService.login({username, password})`.
- On success: navigates to `/admin/dashboard`.
- On failure: shows the backend's `{message}` via snack bar (fallback: `"Invalid username or password."`).

### 3.3 `DashboardComponent` (`src/app/admin/dashboard/`)

Protected by `authGuard`. Two independently-loading sections:

1. **Summary cards** (5 `mat-card` tiles: Total, New, In Progress, Completed, Submitted Today — each with
   a left accent bar + circular Material icon per the visual redesign), bound to
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
   - The `status` column renders the shared `.status-badge` pill (color-coded per status) instead of plain
     text.
   - Loading spinners shown separately for the summary section and the table region; an explicit "No
     submissions found." empty state when a loaded page has zero rows.
   - Per-row "View" button (`viewSubmission()`) navigates to `/admin/submissions/:id` via `Router`.

Every request this component triggers goes through `SubmissionService`, whose admin-facing URLs
(`/api/admin/submissions`, `/api/admin/dashboard/summary`) get the JWT attached automatically by
`authInterceptor` — the component itself has no auth-related code.

Responsive: summary cards `flex-wrap` to stack below 600px; table sits in an `overflow-x: auto` container
with a `min-width` (horizontal scroll rather than column collapse); filters stack vertically below 600px.

### 3.4 `SubmissionDetailComponent` (`src/app/admin/submission-detail/`)

Protected by `authGuard`. Reads `:id` from `ActivatedRoute.snapshot.paramMap` in `ngOnInit`. An
unparseable/missing `id` is treated identically to a backend 404 (`notFound = true`).

States rendered (mutually exclusive, `@if`/`@else if` chain):

| State | Condition | UI |
|---|---|---|
| Loading | `loading` | `mat-spinner` |
| Not found | `notFound` (missing/unparseable id, or backend 404) | "Submission not found." + Back to Dashboard button |
| Load error | `loadError` (any other fetch failure) | "Something went wrong loading this submission." + Back to Dashboard button, plus an error snack bar |
| Loaded | `submission !== null` | Icon-labeled field grid + status editor |

Loaded detail grid fields: Full Name, Email, Phone, Message, Status (as the shared `.status-badge` pill),
Created At, Updated At (`null` email/phone/message rendered as `—`; timestamps formatted with Angular's
`date: 'medium'` pipe). No Company/Position fields (see [field-set deviation](#5-modelsinterfaces) below).
Each field label has a matching Material icon (visual redesign).

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

**No component-level 401 handling** — a 401 on any admin call is caught globally by `authInterceptor`
(logout + redirect to `/admin/login`) before this component's own `error` callback would need to react to
it specifically; see [Section 6](#6-interceptors-guards-and-environment-config).

### 3.5 `AppComponent` — shared nav header

No longer a bare shell. `app.component.html` renders a sticky `.site-header` (gradient green background)
containing:
- A brand link (leaf `mat-icon` + "GreenField Register" text) → `/form`.
- Nav links: "Form" (`/form`), "Admin Dashboard" (`/admin/dashboard`), both with `routerLinkActive`
  highlighting.
- **Auth-aware, reactive** (no page reload): if `authService.isAuthenticated()`, shows the current
  `authService.username()` plus a "Logout" button (`onLogout()` → `authService.logout()` then navigates to
  `/admin/login`); otherwise shows a "Login" link → `/admin/login`.

Collapses to a stacked layout below 600px (`app.component.scss`).

---

## 4. Services

### 4.1 `SubmissionService` (`src/app/core/services/submission.service.ts`)

`providedIn: 'root'`, uses `inject()`. The only place in the app that calls the submission/dashboard API —
no component calls `HttpClient` directly for these.

| Method | HTTP | Endpoint | Request type | Response type |
|---|---|---|---|---|
| `createSubmission(request)` | `POST` | `${apiBaseUrl}/api/submissions` | `CreateSubmissionRequest` | `Observable<Submission>` |
| `listSubmissions(page?, size?, search?, status?)` | `GET` | `${apiBaseUrl}/api/admin/submissions` | query params `page`, `size`, `search`, `status` (each omitted, not sent blank, if undefined/blank) | `Observable<PageResponse<Submission>>` |
| `getSubmission(id)` | `GET` | `${apiBaseUrl}/api/admin/submissions/{id}` | — | `Observable<Submission>` |
| `updateStatus(id, status)` | `PATCH` | `${apiBaseUrl}/api/admin/submissions/{id}/status` | `{ status }` (`UpdateSubmissionStatusRequest` shape) | `Observable<Submission>` |
| `getDashboardSummary()` | `GET` | `${apiBaseUrl}/api/admin/dashboard/summary` | — | `Observable<DashboardSummary>` |

This file was **not modified** by the Phase 17 auth work — `authInterceptor` attaches the bearer token
purely by matching each outgoing request's URL prefix, so this service didn't need to change at all.

### 4.2 `AuthService` (`src/app/core/services/auth.service.ts`)

`providedIn: 'root'`, introduced in Phase 17. Holds and manages all admin auth state for the app.

| Member | Kind | Description |
|---|---|---|
| `login(request: LoginRequest)` | method → `Observable<LoginResponse>` | `POST`s to `${apiBaseUrl}/api/auth/login`; on success, persists `{token, username, role}` |
| `logout()` | method → `void` | Clears in-memory state and `localStorage` |
| `getToken()` | method → `string \| null` | Current bearer token, read by `authInterceptor` |
| `isAuthenticated` | `computed()` signal → `boolean` | Read by `authGuard` and the nav header |
| `username` | `computed()` signal → `string \| null` | Read by the nav header |

**Storage: `localStorage`**, under key `auth`, as `{token, username, role}` JSON — chosen over
`sessionStorage`/in-memory-only. The backend returns the JWT as a JSON response body field, not a cookie,
so an `httpOnly` cookie (immune to JS reads) isn't available without backend changes; given that,
`localStorage`/`sessionStorage` carry the same XSS exposure (either is readable by any script on the page's
origin), and `localStorage` was preferred for surviving a page reload/new tab. State is seeded from
`localStorage` on service construction, so a reload doesn't force re-login. Full tradeoff writeup: this
file's own doc comments and `CHECKLIST.md`'s Phase 17 log entry.

### 4.3 Cross-check against backend DTOs

- `SubmissionController` → `POST /api/submissions`
- `AuthController` → `POST /api/auth/login`
- `AdminSubmissionController` → `GET /api/admin/submissions`, `GET /api/admin/submissions/{id}`,
  `PATCH /api/admin/submissions/{id}/status`
- `DashboardController` → `GET /api/admin/dashboard/summary`

The base URL is built once per resource group from `environment.apiBaseUrl` (no `localhost` hardcoded
anywhere in either service or any component).

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
| `LoginRequest` | `auth.model.ts` | `LoginRequest` (request DTO) | `username: string; password: string` |
| `LoginResponse` | `auth.model.ts` | `LoginResponse` (response DTO) | `token: string; username: string; role: string` |

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
- `LoginRequest.java`/`LoginResponse.java` fields match `auth.model.ts` exactly (verified against
  `backend/src/main/java/com/register/backend/dto/{request/LoginRequest,response/LoginResponse}.java`).

Timestamp/date handling: no dates are ever parsed client-side into JS `Date` objects; they're kept as
opaque strings and only formatted for display via Angular's `date` pipe (`date: 'medium'`) in the detail
and dashboard-table templates. The JWT string itself is likewise never decoded/parsed client-side — it's
stored and sent as an opaque string (see [Section 6](#6-interceptors-guards-and-environment-config)).

---

## 6. Interceptors, Guards, and Environment Config

### 6.1 `authInterceptor` (`core/interceptors/auth.interceptor.ts`)

Functional `HttpInterceptorFn`, registered via `provideHttpClient(withInterceptors([authInterceptor]))` in
`app.config.ts`. Two responsibilities:

1. **Attach the bearer token** — computes `ADMIN_API_PREFIX = ${environment.apiBaseUrl}/api/admin/` once;
   any outgoing request whose URL starts with that prefix gets `Authorization: Bearer <token>` added (via
   `req.clone()`) if a token is currently stored. This is an **allowlist match**, not a denylist — requests
   to `/api/submissions`, `/api/health`, and `/api/auth/login` structurally never match the prefix, so the
   token is never sent there by construction (satisfies the roadmap's "do not send JWT to public endpoints
   unnecessarily" requirement without relying on remembering to exclude specific URLs).
2. **Global 401 handling** — on a `401 HttpErrorResponse` from a request that *was* admin-prefixed, calls
   `authService.logout()` and `router.navigate(['/admin/login'])` before rethrowing the error. This is why
   `DashboardComponent`/`SubmissionDetailComponent` need no 401-specific code of their own.

### 6.2 `authGuard` (`core/guards/auth.guard.ts`)

Functional `CanActivateFn`. Returns `true` if `authService.isAuthenticated()`, otherwise
`router.createUrlTree(['/admin/login'])` (a redirect, not a hard `window.location` navigation). Applied via
`canActivate: [authGuard]` on `/admin/dashboard` and `/admin/submissions/:id` in `app.routes.ts`.

Note: this only checks whether a token is *present* in the auth state, not whether it has already expired
— an expired-but-still-stored token passes the guard, and the first subsequent admin API call then 401s,
which `authInterceptor` catches and redirects from. This is an intentional "let the server be the source of
truth on validity" design, consistent with the backend trusting the token's own claims with no server-side
session.

### 6.3 Environment config

`src/environments/environment.ts` (production) and `environment.development.ts` (development, swapped in
via `angular.json`'s `fileReplacements` for the `development` build configuration). Both currently set:
```ts
{ production: <bool>, apiBaseUrl: 'http://localhost:8080' }
```
`environment.apiBaseUrl` is the single source of the backend's base URL, consumed by `SubmissionService`,
`AuthService`, and `authInterceptor` alike. No production backend is deployed yet, so both environments
point at the same local URL for now — the production file is deliberately left with a comment flagging it
needs updating at deploy time (a later roadmap phase, "Phase 24 — Deploy Angular to Vercel").

### 6.4 CORS (backend side, relevant to the frontend's ability to call the API)

`backend`'s `CorsConfig` allows origins from `app.cors.allowed-origins` (env var `ALLOWED_ORIGINS`, default
`http://localhost:4200` — the default Angular dev-server port, i.e. `ng serve`), methods `GET, POST,
PATCH, OPTIONS`, headers `Content-Type, Authorization`. No `allowCredentials`, no wildcard origin. `OPTIONS`
preflight is explicitly permitted without authentication on the backend's security filter chain, so
preflight to protected `/api/admin/**` routes isn't itself blocked.

### 6.5 App-level providers (`src/app/app.config.ts`)

`provideZoneChangeDetection({ eventCoalescing: true })`, `provideRouter(routes)`,
`provideHttpClient(withInterceptors([authInterceptor]))`, `provideAnimationsAsync()` (Angular Material's
async animations loader — needed for Material components like `mat-select`/`mat-snack-bar` to animate).

---

## 7. How to Run

See `clientUI/README.md` (standard Angular CLI generated instructions: `ng serve`, `ng build`, `ng test`)
and the root `CLAUDE.md` for how this fits into the wider project (backend must be running separately on
`http://localhost:8080` — see `CLAUDE.md`'s Commands section — for any of the pages to successfully load or
submit data; the public form and admin pages will otherwise show HTTP error snack bars). No proxy config
file (`proxy.conf.json`) exists — the frontend talks to the backend directly cross-origin, relying on the
backend's CORS configuration described above rather than a dev-server proxy.

To exercise the admin flow locally: start the backend, log in at `/admin/login` with the seeded dev
credentials (default `admin` / see `README.md`'s env var table — override via `ADMIN_USERNAME`/
`ADMIN_PASSWORD` before any real deployment), then navigate to `/admin/dashboard`.

---

## Not Yet Implemented

- **No shared/layout components beyond the nav header.** `src/app/shared/` is still empty (just a
  `.gitkeep`) — no other reusable UI pieces exist yet.
- **No wildcard/404 route** in `app.routes.ts` for unmatched client-side paths.
- **No client-side sorting UI** on the dashboard table — the backend's default `createdAt DESC` ordering is
  used as-is; no `MatSort` is wired up.
- **No token-expiry-aware UX** beyond the reactive 401 → logout → redirect flow — e.g. no "your session is
  about to expire" warning, no silent token refresh (the backend issues no refresh tokens).
- **Docker / deployment (roadmap Phases 20, 24)** — no Angular Dockerfile, no Vercel deployment configured
  yet; `environment.ts`'s `apiBaseUrl` still points at `localhost:8080`.
