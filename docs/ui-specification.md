# Angular Frontend (`clientUI/`) — Technical Specification

**Status as of this document:** Roadmap Phases 11–17 (Angular setup through admin JWT auth) are
implemented, **plus** the full **DriveUp UI/UX redesign** (Plan 1 reskin + Plan 2 D1–D6 domain adoption —
see [`docs/planning/`](planning/)): a blue/orange design system replacing the earlier green "landscaping"
theme, a sidebar+topbar admin shell with three real pages (Overview/Students/Courses), and a full 8-section
public landing page replacing the old single-field `/form`. See [Not Yet Implemented](#not-yet-implemented)
for what's still open.

This document describes the actual current implementation under `clientUI/src/`, cross-checked against the
real source files. For the phased plan and rationale behind deviations, see
`java-spring-boot-angular-project-prompts.md`, `docs/planning/plan-1-ui-reskin-ngan-han.md`,
`docs/planning/plan-2-full-redesign-driveup.md`, and `CHECKLIST.md` at the repo root.

---

## 1. Tech Stack

Sourced from `clientUI/package.json`.

| Package | Version |
|---|---|
| Angular (`@angular/core`, `common`, `forms`, `router`, `platform-browser*`, `compiler`) | ^19.2.0 |
| `@angular/animations` | ^19.2.25 |
| `@angular/cdk` | ^19.2.19 |
| `@angular/material` | ^19.2.19 |
| `@lucide/angular` | ^1.47.0 |
| `rxjs` | ~7.8.0 |
| `zone.js` | ~0.15.0 |
| `typescript` | ~5.7.2 |
| Testing: `jasmine-core`, `karma` + plugins | see `package.json` |

Notes:
- All components are **standalone** — no `NgModule`s anywhere besides what Angular Material provides.
- **Design system (Plan 1 reskin)**: `src/styles.scss` defines a **DriveUp-inspired** design system —
  primary blue `#2b5fff`, accent orange `#f97316`, ink `#12172b` — via a **custom M3 Material theme**
  generated from those exact hex codes (`ng generate @angular/material:theme-color`, output in
  `src/_theme-colors.scss`, since neither color matches a built-in Material palette) plus CSS custom
  properties (`--color-primary`, `--color-bg-admin`, `--radius-*`, `--sidebar-*`, etc.). Fonts: **Sora**
  (600/700/800, headings) and **Manrope** (400–800, body), loaded via Google Fonts in `index.html`. Design
  principles carried through from the DriveUp mockup: no gradients, no left-border cards (an earlier
  left-border KPI-card pattern was explicitly removed during the reskin), outline icons only (`@lucide/angular`,
  not Material Icons — the Material Icons font `<link>` was removed from `index.html`).
- Icon usage: each component imports only the specific `@lucide/angular` icon components it renders (e.g.
  `LucideSearch`, `LucideLogOut`) directly into its own `imports` array — there is no
  `LucideAngularModule.pick({...})` registration step with this package version; importing the icon
  component is the registration.

---

## 2. App Structure

### 2.1 Routing

Defined in `src/app/app.routes.ts`, registered via `provideRouter(routes)` in `src/app/app.config.ts`.

| Path | Component | Guard | Notes |
|---|---|---|---|
| `''` | `LandingPageComponent` | none | **Public landing page** (D6) — was a redirect to `/form` before Plan 2 |
| `/form` | — | none | Redirects to `''` (backward-compat for old links; `InformationFormComponent` was deleted) |
| `/admin/login` | `LoginComponent` | none | Public, full-page (no sidebar) |
| `/admin` | `AdminLayoutComponent` | — (parent, no guard itself) | Sidebar + topbar shell, wraps all admin pages below as children |
| `/admin/overview` | `OverviewComponent` | `authGuard` | KPIs, month chart, upcoming courses, recent registrations (D5) |
| `/admin/students` | `StudentsComponent` | `authGuard` | Full searchable/filterable/paginated submissions table (renamed in place from `DashboardComponent`) |
| `/admin/courses` | `CoursesComponent` | `authGuard` | Course table + create/edit dialog (D5) |
| `/admin/dashboard` | — | — | Redirects to `/admin/overview` (backward-compat for old bookmarks) |
| `/admin/submissions/:id` | `SubmissionDetailComponent` | `authGuard` | Submission detail/status update |

`authGuard` (`core/guards/auth.guard.ts`, unchanged since Phase 17) redirects an unauthenticated visitor
to `/admin/login` via `Router.createUrlTree`. Still no wildcard (`**`) "not found" route.

### 2.2 Folder Layout

```
clientUI/src/
├── app/
│   ├── admin/
│   │   ├── layout/                    AdminLayoutComponent — sidebar + topbar shell (Plan 1)
│   │   ├── overview/                  OverviewComponent (D5)
│   │   ├── students/                  StudentsComponent (D5, was admin/dashboard/)
│   │   ├── courses/                   CoursesComponent (D5) + courses/course-form-dialog/
│   │   ├── login/                     LoginComponent
│   │   └── submission-detail/         SubmissionDetailComponent
│   ├── core/
│   │   ├── guards/auth.guard.ts
│   │   ├── interceptors/auth.interceptor.ts
│   │   └── services/
│   │       ├── auth.service.ts
│   │       ├── submission.service.ts  submission CRUD/list only (dashboard endpoints moved out, D5)
│   │       ├── course.service.ts      course CRUD/list, public + admin (D4/D5)
│   │       └── dashboard.service.ts   summary/overview/settings endpoints (D5)
│   ├── models/
│   │   ├── auth.model.ts
│   │   ├── submission.model.ts        Submission (4-state status + courseId), CreateSubmissionRequest, ...
│   │   ├── course.model.ts            Course, LicenseClass, CourseAvailabilityStatus, Create/UpdateCourseRequest (D1/D4)
│   │   ├── dashboard-overview.model.ts DashboardOverview, MonthlyRegistrationCount, DashboardSettings (D5)
│   │   ├── page-response.model.ts
│   │   └── dashboard-summary.model.ts DashboardSummary (4-state shape)
│   ├── public/
│   │   └── landing/                   LandingPageComponent (D6) — replaces the deleted public/information-form/
│   ├── shared/                        still empty (.gitkeep)
│   ├── app.component.ts/.html/.scss/.spec.ts   bare <router-outlet> (Plan 1 — the old shared top-nav moved into AdminLayoutComponent / the landing page's own nav)
│   ├── app.config.ts
│   └── app.routes.ts
├── environments/
├── index.html                         Sora/Manrope fonts (no Material Icons font)
├── main.ts
├── styles.scss                        DriveUp design tokens, custom Material M3 theme
└── _theme-colors.scss                 Generated M3 tonal palette for #2b5fff / #f97316
```

---

## 3. Components

| Component | Route | Purpose |
|---|---|---|
| `LandingPageComponent` | `/` | Public 8-section marketing + registration page (D6) |
| `LoginComponent` | `/admin/login` | Admin login form |
| `AdminLayoutComponent` | `/admin` (parent) | Dark sidebar (264px) + topbar (76px) shell for all admin pages |
| `OverviewComponent` | `/admin/overview` | KPI cards, month chart, upcoming courses, recent registrations |
| `StudentsComponent` | `/admin/students` | Full submissions table (search/status/course filters, pagination) |
| `CoursesComponent` | `/admin/courses` | Course table + create/edit `MatDialog` |
| `SubmissionDetailComponent` | `/admin/submissions/:id` | Single submission detail + status update |

### 3.1 `AdminLayoutComponent` (`admin/layout/`)

Introduced in Plan 1, extended in D4 with the 3-item nav. Dark sidebar (`--sidebar-bg`) with:
- Brand mark, "MENU" section label, 3 `routerLink`+`routerLinkActive` items: **Overview**, **Students**,
  **Courses** (Lucide icons: `LucideLayoutDashboard`, `LucideUsers`/equivalent, `LucideCalendarDays`/
  `LucideClipboardList` — see the component's actual imports).

Topbar (white, 76px):
- **Search relay** — a duck-typed `SearchableRouteComponent` interface (`{ searchControl: FormControl<string>
  }`); on the nested `<router-outlet>`'s `(activate)` event, `AdminLayoutComponent` checks whether the newly
  activated page exposes a `searchControl` and, if so, relays the topbar's search box into that **same
  `FormControl` instance** (not a copy) — currently only `StudentsComponent` exposes one, so the search box
  is hidden on Overview/Courses. This means the layout never implements its own filtering logic, it purely
  forwards.
- Current admin's initial-letter avatar + username (`AuthService.username()`) + logout button
  (`AuthService.logout()` then navigate to `/admin/login`).
- A mobile hamburger toggle (`sidebarOpen` signal) collapses the sidebar to an off-canvas drawer on narrow
  viewports.

`admin/login` is intentionally **outside** this layout (full-page card, no sidebar).

### 3.2 `LandingPageComponent` (`public/landing/`)

Replaces `InformationFormComponent` (deleted). All 8 sections from
`driveup-claude-cli-prompt-design-UI-UX.md` §2, single scrolling page:

1. **Nav** — sticky, "DriveUp" brand (car icon), anchor links to `#features`/`#courses`/`#process`/`#reviews`,
   hotline, pill CTA scrolling to `#dangky`.
2. **Hero** — headline + description + 2 CTAs + a 3-stat row; a flat (no gradient) illustration panel with
   a car icon and a floating "Đã đăng ký thành công!" badge.
3. **Features** (`#features`) — static 4-card grid (hardcoded content, not backend-driven — these are
   fixed marketing bullets, not data).
4. **Courses** (`#courses`) — **wired to the real, public `GET /api/courses`** via `CourseService.
   listPublicCourses()`, not mock data. The B2 card gets the "most popular" treatment
   (`isPopular(course)` → `licenseClass === 'B2'`). A companion Flyway seed migration
   (`V5__seed_landing_courses.sql`, backend-side) populates the 3 fixed course packages so this section
   isn't empty on a fresh database. `courseFeatures(course)` synthesizes the card's bullet list from real
   `durationMonths`/`practiceHours` plus a small per-license-class phrase override (there's no "feature
   list" field on the backend `Course` to source this from directly).
5. **Process** (`#process`) — static 4-step flow (`processSteps`, hardcoded content).
6. **Reviews** (`#reviews`) — static 3 testimonial cards (`testimonials`, hardcoded — names/roles/quotes
   verbatim from the design doc).
7. **Registration form** (`#dangky`) — `FormBuilder` reactive form: `fullName`/`phone` (Vietnamese mobile
   pattern `^(0|\+84)(3|5|7|8|9)[0-9]{8}$`)/`email`/`licenseClass`, **all required** (stricter than the
   backend's `CreateSubmissionRequest`, which makes `email`/`phone` optional — a deliberate per-page choice
   matching this page's own design spec). `onSubmit()` resolves the selected `licenseClass` to a `courseId`
   by picking the first currently-loaded course of that class (`resolveCourseId`) — omitted from the
   request if none is loaded, since `courseId` is optional server-side. On success: hides the form, shows a
   success state (`submitted` flag) rather than navigating away.
8. **Footer** — static 4-column dark footer.

`selectCourseClass(licenseClass)` — clicking a pricing card's CTA pre-fills the registration form's
`licenseClass` dropdown (the anchor `href` handles the scroll).

### 3.3 `OverviewComponent` (`admin/overview/`)

New in D5. Loads two independent things in `ngOnInit` (a failure in one doesn't block the other):

- `DashboardService.getDashboardOverview()` → 4 KPI cards (**New Students This Month** = the last entry of
  `monthlyRegistrations`; **Revenue This Month (est.)** = `estimatedRevenueThisMonth`, formatted via
  `Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'})`; **Open Courses** =
  `upcomingCourses.length`; **Pass Rate** = `settings.passRatePercent`, or `'—'` if never configured) + a
  **month-by-month bar chart** (`buildChartBars` computes each bar's height as a percentage of the largest
  count in the series — **plain flexbox/div bars, no charting library**, per the project's
  no-unnecessary-dependency convention) + an **upcoming courses** list (from the same response, with a
  "View all" link to `/admin/courses`).
- `SubmissionService.listSubmissions(0, 5)` → a short **recent registrations** table (newest-first, the
  backend's default sort), with a "View all" link to `/admin/students`.

### 3.4 `StudentsComponent` (`admin/students/`, formerly `DashboardComponent`)

Repurposed in place during D5 (git history shows this as a rename, not a delete+recreate). Same
search/pagination mechanics as before Plan 2, extended with:
- Status filter options now the 4 new values (`PENDING_CONSULTATION`/`CONFIRMED`/`IN_PROGRESS`/`GRADUATED`).
- New **course filter** dropdown (`courseControl`), populated once from `CourseService.
  listCoursesForAdmin(0, 100)` (a flat list, not a searchable picker — deliberately simple).
- KPI summary cards **removed** from this page — they moved to `OverviewComponent`; this page is now
  purely the table + filters + pagination.
- Status badges use the shared `.status-badge` CSS pattern (`styles.scss`), extended with a 4th color
  variant for `CONFIRMED` (blue) alongside the existing purple/amber/green for the other three.

### 3.5 `CoursesComponent` (`admin/courses/`)

New in D5. Server-side paginated table: name, start date, teacher, a **seats progress bar** (colored by
`availabilityStatus` — green `AVAILABLE`, amber `FILLING_UP`, red `FULL`, matching the same 3-color
semantic used elsewhere), edit action. Toolbar: `licenseClass` dropdown (`ALL`/`B1`/`B2`/`C`) and a
**branch** dropdown populated from a one-time unfiltered load's distinct branch values (`loadBranchOptions`)
— no status/availability filter, since the backend doesn't support filtering on that derived field.

"+ Thêm khoá học" button opens `CourseFormDialogComponent` (a `MatDialog`, `admin/courses/
course-form-dialog/`) with a Reactive Form matching `CreateCourseRequest`/`UpdateCourseRequest` — same
dialog handles both create (`data.course === null`) and edit (`data.course` populated). Native
`<input type="date">` is used for `startDate` rather than `MatDatepickerModule`, to avoid pulling in a new
date-adapter provider dependency. On the dialog closing with a result, the parent reloads both the course
list and the branch filter options (a newly created course might introduce a new branch value).

### 3.6 `SubmissionDetailComponent`, `LoginComponent`

Unchanged in structure since Phase 17/the Plan 1 reskin — see this doc's git history for the full
per-state breakdown (loading/not-found/error/loaded) if needed; only the visual tokens and status-badge
palette changed, not the logic.

---

## 4. Services

| Service | File | Responsibility |
|---|---|---|
| `SubmissionService` | `core/services/submission.service.ts` | `createSubmission`, `listSubmissions` (now with an optional `courseId` param), `getSubmission`, `updateStatus`. **No longer owns dashboard endpoints** (moved to `DashboardService` in D5). |
| `CourseService` | `core/services/course.service.ts` | `listPublicCourses` (public `GET /api/courses`), `listCoursesForAdmin` (filtered), `getCourse`, `createCourse`, `updateCourse` |
| `DashboardService` | `core/services/dashboard.service.ts` | `getDashboardSummary`, `getDashboardOverview`, `getDashboardSettings`, `updateDashboardSettings` |
| `AuthService` | `core/services/auth.service.ts` | Unchanged since Phase 17 — login/logout, signal-based auth state in `localStorage` |

All follow the same `providedIn: 'root'`/`inject(HttpClient)`/`environment.apiBaseUrl`-prefixed-URL/JSDoc
convention established in Phase 12. `authInterceptor` (unchanged) attaches the bearer token to any request
whose URL starts with `${apiBaseUrl}/api/admin/` — this covers the new `/api/admin/courses` and
`/api/admin/dashboard/*` endpoints automatically, no interceptor changes were needed for Plan 2.

---

## 5. Models

All under `src/app/models/`, documented in-source as mirroring specific backend DTOs — see each file's own
JSDoc for the exact field-by-field correspondence. Summary of what changed in Plan 2:

| File | Change |
|---|---|
| `submission.model.ts` | `SubmissionStatus` → 4 values; `Submission`/`CreateSubmissionRequest` gained `courseId` |
| `dashboard-summary.model.ts` | `DashboardSummary` → new 5-count shape (`total`, `pendingConsultation`, `confirmed`, `inProgress`, `graduated`, `submittedToday`) |
| `course.model.ts` | **New** — `Course`, `LicenseClass`, `CourseAvailabilityStatus`, `CreateCourseRequest`, `UpdateCourseRequest` |
| `dashboard-overview.model.ts` | **New** — `DashboardOverview`, `MonthlyRegistrationCount`, `DashboardSettings`, `UpdateDashboardSettingsRequest` |

Same conventions as before: no dates ever parsed into JS `Date` objects (kept as opaque ISO strings,
formatted only via Angular's `date` pipe at render time); the JWT is likewise never decoded client-side.

---

## 6. Interceptors, Guards, and Environment Config

Unchanged since Phase 17 — `authInterceptor` (allowlist-matches `/api/admin/` URLs, handles global 401 →
logout + redirect) and `authGuard` (checks `AuthService.isAuthenticated()`, redirects to `/admin/login`)
both needed **zero changes** for Plan 2, since every new endpoint follows the same `/api/admin/**` URL
convention the interceptor already matches.

`environment.ts`'s `apiBaseUrl` points at the live production backend
(`https://backed-website-register.onrender.com`) as of the Phase 23 deployment — see `README.md` and
`docs/deployment/render-backend-deployment.md`.

---

## 7. How to Run

See `clientUI/README.md` and the root `CLAUDE.md`. Backend must be running (locally on `:8080`, or point
`environment.development.ts` at the live Render instance) for any page to load real data. To exercise the
full admin flow: log in at `/admin/login`, land on `/admin/overview`, navigate via the sidebar to Students/
Courses. To exercise the public flow: visit `/`, scroll to `#dangky`, submit — the created submission is
immediately visible in `/admin/students` (cross-checked end to end during D6's verification).

---

## Not Yet Implemented

- **No shared/layout components beyond the admin sidebar.** `src/app/shared/` is still empty.
- **No wildcard/404 route.**
- **No client-side sorting UI** on the Students table (backend default `createdAt DESC` used as-is).
- **No token-expiry-aware UX** beyond the reactive 401 → logout → redirect flow.
- **Week-strip mini-calendar** on the Courses page (from the original mockup) — skipped in D5 as
  disproportionate effort for a first pass; the table + filters + create/edit dialog are the load-bearing
  parts of that page.
- **Vercel deployment** — code is ready (see `docs/deployment/vercel-frontend-deployment.md`), but no
  Vercel project has actually been created from this environment (no credentials).
- **Minor UI language inconsistency**: the Courses page's main action button label was left in Vietnamese
  ("Thêm khoá học") while the dialog itself and most other UI text is English — noted during review, not
  yet fixed.
