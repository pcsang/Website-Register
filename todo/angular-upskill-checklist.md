# DriveUp — Angular Upskilling Plan & Checklist

Scoped to `clientUI/` only. 13 modules, ordered so each one unlocks the next — testing (Module 4) needs the fundamentals refresh in Module 1 before it's worth doing, and performance work (Module 8) needs tests in place before you can trust that a change didn't break anything. Within a module, items are roughly independent — pick them up in any order.

Each item names the real file(s) it touches. Check items off as you go; this doc is meant to be lived in, not read once.

---

## Module 0 — Orientation

**Goal:** have the whole app's shape in your head before changing anything.

- [ ] Read `app.routes.ts` top to bottom and sketch the route tree on paper, including which routes sit under `AdminLayoutComponent` and which ones carry `canActivate: [authGuard]`
- [ ] Read `core/services/auth.service.ts` end to end — note the `signal`/`computed` pair (`authState`, `isAuthenticated`, `username`) and how `localStorage` seeds it on construction
- [ ] Read the other 5 services (`course.service.ts`, `submission.service.ts`, `dashboard.service.ts`, `payment.service.ts`, `admin-user.service.ts`) and write one sentence per service describing its job
- [ ] Open `clientUI/src/app/shared/` — note it's empty but for `.gitkeep`. Keep this in mind for Module 10.

---

## Module 1 — Unblock, then refresh fundamentals

**Skill:** standalone components, Angular signals.

- [ ] Fix `admin/submission-detail/submission-detail.component.spec.ts` — its `MOCK_SUBMISSION` is missing the now-required `assignedToId` field from the `Submission` model (`models/submission.model.ts`), which currently blocks `ng test` from compiling at all. Do this first; nothing else in Module 4 is testable until it's fixed.
- [ ] Confirm every component in the app is standalone (`grep -r "standalone" clientUI/src/app` or just note there are zero `NgModule` files) — write down why that's the default in Angular 19 and what you'd lose/gain going back to modules
- [ ] Pick one component with plain-field local state (e.g. a filter value in `admin/courses/courses.component.ts`) and convert it to a `signal`, mirroring the pattern in `auth.service.ts`. Add a `computed()` derived from it.
- [ ] Revert or keep that change deliberately — either way, write one paragraph on when you'd reach for a signal vs. a plain class field vs. an `Observable`

---

## Module 2 — Reactive Forms

**Skill:** `FormGroup`/`FormControl`, validators, `MatDialog` form data flow.

- [ ] Read the registration form in `public/landing/landing-page.component.ts` — note it requires `fullName`/`phone`/`email`/`courseId` client-side even though the backend makes `email`/`phone` optional (confirmed in `docs/backend-specification.md`)
- [ ] Decide and implement a fix for that client/server validation mismatch — either relax the client-side validators to match the backend, or justify in a code comment why the stricter UX is intentional
- [ ] Read `admin/courses/course-form-dialog/course-form-dialog.component.ts` for the `MatDialog` pattern: how initial data is injected (`MAT_DIALOG_DATA`), how the form is pre-filled for edit vs. create, and how the result is returned via `dialogRef.close(...)`
- [ ] Add a custom validator (e.g. a Vietnamese phone number format check) to the registration form, and write a focused unit test for the validator function itself (not the whole component)

---

## Module 3 — RxJS, HTTP services, interceptors

**Skill:** operators, `HttpClient`, `HttpInterceptorFn`.

- [ ] Read `core/interceptors/auth.interceptor.ts` — note exactly where the bearer token is attached and how a 401 is handled
- [ ] Read the debounced search in `admin/students/students.component.ts:136` — `.pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged())` — and explain in your own words why both operators are needed (what breaks if you drop one)
- [ ] `extractErrorMessage` is currently duplicated across 7 components (`landing-page`, `submission-detail`, `students`, `overview`, `login`, `courses`, `course-form-dialog`). Pull it into one place — either a shared pure function in `core/` or folded into a new error-handling `HttpInterceptorFn` — and delete the 7 copies.
- [ ] Implement payment-status polling in `admin/submission-detail/submission-detail.component.ts` to replace the current manual-refresh-only `refreshPaymentStatus()`, using `interval()` + `switchMap()` (stop polling once status leaves `PENDING`)

---

## Module 4 — Component & service testing (the biggest real gap)

**Skill:** `TestBed`, `HttpTestingController`, Jasmine spies.

> As of this audit: **zero** of the 6 services have a spec file, `auth.guard.ts` and `auth.interceptor.ts` have zero tests, and 7 component specs are untouched CLI scaffolds with no real assertions. This module is the highest-leverage one in the whole plan.

- [ ] Write `auth.service.spec.ts` — mock `HttpClient` via `HttpTestingController`, assert `login()` persists to `localStorage` and flips `isAuthenticated()`, assert `logout()` clears both
- [ ] Write `auth.guard.spec.ts` — assert it allows navigation when authenticated and redirects to `/admin/login` when not
- [ ] Write `auth.interceptor.spec.ts` — assert the bearer token is attached to outgoing requests and a 401 response triggers logout/redirect
- [ ] Write spec files for the remaining 5 services: `course.service.spec.ts`, `submission.service.spec.ts`, `dashboard.service.spec.ts`, `payment.service.spec.ts`, `admin-user.service.spec.ts`
- [ ] Rewrite `admin/login/login.component.spec.ts` beyond "should create" — assert a failed login shows an error, a successful one navigates to `/admin/overview`
- [ ] Rewrite `admin/overview/overview.component.spec.ts` — assert KPI cards render from a mocked `DashboardService` response
- [ ] Rewrite `admin/students/students.component.spec.ts` — assert the search/status/course/assigned-to filters actually call the service with the right params
- [ ] Rewrite `admin/courses/courses.component.spec.ts` and `course-form-dialog.component.spec.ts` — assert create/edit submits the right payload and closes the dialog
- [ ] Rewrite `admin/layout/admin-layout.component.spec.ts` — assert the sidebar nav and logout button work
- [ ] Rewrite `app.component.spec.ts` — assert the router outlet renders (this one can stay light)
- [ ] Add missing assertions to `admin/submission-detail/submission-detail.component.spec.ts` for the assignment dropdown and notes thread (`assignSubmission()`, `loadNotes()`, `addNote()`) — currently untested despite being the largest component in the app
- [ ] Run `ng test --code-coverage` once the above is done and record the baseline number — this becomes your regression tripwire for every later module

---

## Module 5 — End-to-end testing (Playwright)

**Skill:** Playwright page objects/fixtures, test data setup.

> `tests/example.spec.ts` at the repo root is still the default scaffold — it asserts against `playwright.dev`, not this app. There is currently zero real e2e coverage.

- [ ] Replace `tests/example.spec.ts` with a real first test: load `/`, fill the registration form, submit, assert a success state
- [ ] Add an e2e test for admin login (`/admin/login` → `/admin/overview`) and for the logout round-trip
- [ ] Add an e2e test that creates a submission, then in the admin UI assigns it to an admin user and adds a note (covers the Admin User Management feature end-to-end)
- [ ] Add an e2e test for the payment flow: generate a VietQR code from `submission-detail`, assert the QR image and payment code render
- [ ] Introduce a Playwright page-object or fixture pattern once you have 3+ specs, so selectors aren't copy-pasted across tests

---

## Module 6 — Angular Material & CDK

**Skill:** `MatDialog`, `MatSnackBar`, CDK overlay basics.

- [ ] Study every `MatSnackBar` call in the app (grep for it) and note the inconsistent error-message formatting this module should have already fixed in Module 3
- [ ] Build one new reusable component: a `ConfirmDialogComponent` in `shared/` (first real tenant of that empty folder), wired through `MatDialog`
- [ ] Use the new `ConfirmDialogComponent` for a real action — e.g. confirming before cancelling/force-approving a stuck `PENDING` payment, if that backend action exists yet, or confirming before deleting/archiving a course
- [ ] Read up on CDK overlay positioning and explain why `MatDialog` already handles this for you — you don't need to hand-roll it

---

## Module 7 — Accessibility

**Skill:** ARIA attributes, keyboard navigation, contrast.

> Only 5 `aria-*`/`role`/`alt` attributes exist across the entire `clientUI/src/app` tree today.

- [ ] Run an automated audit (`axe-core` browser extension, or Lighthouse's accessibility tab) against `/`, `/admin/login`, `/admin/overview`, `/admin/students`, `/admin/courses`, and a submission detail page — record the findings before fixing anything
- [ ] Add `aria-label`s to every icon-only button (Lucide icons are used throughout — grep for `<lucide-icon` to find them) in `admin/layout/admin-layout.component.html` and `admin/submission-detail/submission-detail.component.html`
- [ ] Verify keyboard-only navigation works through the sidebar, the course create/edit dialog, and the mobile hamburger drawer
- [ ] Check the status badges/chips (submission status, payment status, seat-progress bars) against WCAG AA contrast using the actual SCSS tokens in `styles.scss` / `_theme-colors.scss`

---

## Module 8 — Performance & change detection

**Skill:** `ChangeDetectionStrategy.OnPush`, bundle analysis.

> Zero components in the app currently use `OnPush` — everything runs default change detection.

- [ ] Pick 2–3 presentational components with no internal mutation (e.g. the KPI cards or upcoming-courses list in `admin/overview/overview.component.ts`) and switch them to `ChangeDetectionStrategy.OnPush`. Confirm nothing breaks using the test suite from Module 4.
- [ ] Explain in your own words why `OnPush` is safe for those specific components but would need more care on something like `admin/students/students.component.ts`, which mutates filter state directly
- [ ] Run `ng build --configuration production --stats-json` and inspect the output with `webpack-bundle-analyzer` (or `source-map-explorer`) — check that `@lucide/angular` icon imports are per-icon and not pulling in the full icon set
- [ ] Note (don't rebuild) that the Overview bar chart is a deliberate hand-rolled flexbox/div chart, not a charting library — as a side exercise, prototype the same chart with a lightweight library (e.g. ECharts or Chart.js) in an isolated branch, purely to learn the library; compare bundle-size cost against the hand-rolled version before deciding whether it's worth merging

---

## Module 9 — Routing & UX completeness

**Skill:** functional guards (`CanActivateFn`), wildcard routes.

- [ ] Add a wildcard route (`{ path: '**', component: NotFoundComponent }`) to `app.routes.ts` — there is currently none
- [ ] Read `core/guards/auth.guard.ts`'s functional `CanActivateFn` style, then write a second guard as practice — e.g. a `roleGuard` stub that checks `authService` for a specific role, even though only one admin role exists today
- [ ] Fix the label inconsistency on the Courses page ("+ Thêm khoá học" is Vietnamese while the rest of that page/dialog is English) — pick one language convention and apply it consistently
- [ ] Confirm the payment-polling change from Module 3 degrades gracefully if a user navigates away mid-poll (unsubscribe on destroy)

---

## Module 10 — Shared component library

**Skill:** extracting reusable UI from duplicated markup.

- [ ] Audit the admin pages for repeated UI patterns that aren't yet shared components — status badges/chips, the seat-progress bar, the loading-spinner/empty-state pattern each page currently hand-rolls
- [ ] Extract at least 2 of those into `shared/` components with a typed `@Input()` API (e.g. `StatusBadgeComponent`, `EmptyStateComponent`)
- [ ] Replace their inline duplicates across the admin components and re-run the Module 4 test suite to confirm behavior didn't shift

---

## Module 11 — Docs & handoff

**Skill:** writing documentation that matches shipped code, not aspirational code.

- [ ] Update `docs/ui-specification.md` to document the Admin User Management UI: the "assigned to" filter/column in `students.component.ts`, and the assignment dropdown + notes thread in `submission-detail.component.ts` — both are shipped but currently undocumented
- [ ] Update `PAGES.md`'s Students and Submission-Detail sections to match
- [ ] Write a short note (a paragraph is enough) on why this app has no NgRx/Akita — state lives in per-component signals/fields plus a few `providedIn: 'root'` services — so a future reader doesn't wonder if it was an oversight

---

## Module 12 — Stretch: framework upgrade practice

**Skill:** `ng update`, reading migration guides, upgrading with a safety net.

> Do this **last** — it only makes sense once Module 4's test suite exists to catch a regression.

- [ ] Run `ng update` with no arguments to see what's outdated, then `ng update @angular/core @angular/cli --dry-run` against the latest major and read what it reports
- [ ] On a throwaway branch, actually run the upgrade, fix whatever breaks, and run the full Module 4 + Module 5 suite before deciding whether to bring it back to `develop`
- [ ] Write down what changed (deprecations, new defaults, codemods that ran automatically) as your own migration notes

---

### How to track this

Treat each checked box as its own small commit or PR — small enough to review in one sitting. Modules 1 and 4 are the ones with real urgency (one's a currently-broken build, the other is the biggest coverage gap); everything from Module 6 onward is genuinely optional pacing, not a hard sequence.
