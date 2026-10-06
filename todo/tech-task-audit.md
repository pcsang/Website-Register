# DriveUp — Tech Task Audit &amp; Upskilling Route

Engineering memo — reviewed 2026-10-06 at commit `fb26d53`.
Stack: Spring Boot 3.4 / Java 21 (`backend/`) · Angular 19.2 (`clientUI/`).

A component-by-component list of what's outstanding across the backend, frontend,
database, CI/CD, observability, and security — turned into a sequenced curriculum
for working through it solo.

## Where things stand

All 27 roadmap phases plus three out-of-roadmap initiatives (UI reskin, the full
DriveUp redesign, Admin User Management) are shipped and live —
`backed-website-register.onrender.com` / `website-register-roan.vercel.app`. The
gaps below aren't "unfinished features" so much as the debt every fast-moving solo
project accrues: thin test coverage in a few specific places, one broken test file,
no CI for the backend at all, and zero production observability. None of it is on
fire — it's a solid list to learn from.

Priority legend: **Critical** = broken or blocking right now · **High** = real
exposure, no coverage · **Medium** = real gap, not urgent · **Low** = worth doing,
no pressure.

---

## Task board, by component

### Backend

- [ ] **Critical** — `docs/backend-specification.md` is missing the entire Admin
  User Management feature: `AdminUserController`, assignment, notes, the `V7`
  migration don't exist in the doc
- [ ] **High** — No `AuthServiceTest`; no unit tests for `JwtService`,
  `JwtAuthenticationFilter`, `SecurityConfig`, the entry-point/access-denied
  handlers — all only exercised indirectly via one integration test
- [ ] **High** — `PaymentMapper` (builds the VietQR URL + payment code) has no
  test anywhere that asserts its actual output — it's mocked away everywhere it's
  used
- [ ] **Medium** — No `@WebMvcTest` slice for `SepayWebhookController`
- [ ] **Medium** — No scheduled job or `EXPIRED` status for stale `PENDING`
  payments, and no admin action to cancel/force-approve one stuck
- [ ] **Low** — SePay webhook field names, its `Authorization: Apikey` scheme,
  and VietQR bank-code format are unverified guesses against undocumented
  behavior
- [ ] **Low** — `CourseRepository` filters only by `licenseClass`/`branch` — a
  derived availability-status filter is deferred

### Frontend

- [ ] **Critical** — `submission-detail.component.spec.ts`'s mock is missing the
  now-required `assignedToId` field — this is a live TypeScript error blocking
  `ng test` from compiling at all, right now
- [ ] **High** — `auth.guard.ts` and `auth.interceptor.ts` — the entire
  client-side auth flow — have zero real tests
- [ ] **High** — 7 component specs are untouched CLI scaffolds ("should
  create", no assertions): courses, course-form-dialog, admin-layout, login,
  overview, students, app.component
- [ ] **High** — None of the 6 services (`auth`, `submission`, `course`,
  `dashboard`, `payment`, `admin-user`) have a spec file
- [ ] **Medium** — The Playwright e2e suite is still the default scaffold — it
  asserts against `playwright.dev`, not this app
- [ ] **Medium** — `extractErrorMessage` is hand-copied into ~7 components
  instead of living in one place
- [ ] **Low** — No wildcard/404 route; payment status is manual-refresh only,
  no polling
- [ ] **Low** — Only 5 `aria-*`/`role`/`alt` attributes exist across the whole
  app

### Database

- [ ] **Medium** — No indexes on the columns actually filtered on — submission
  status, course `licenseClass`/`branch`, `assignedToId`
- [ ] **Medium** — Course listing runs an extra `COUNT` query per page load —
  a known N+1-adjacent pattern flagged in the architecture review, never fixed
- [ ] **Low** — Local Postgres on this machine is WIN1252-encoded, not UTF8 —
  breaks the Vietnamese seed data in `V5` until the DB is recreated

### CI / CD

- [ ] **Critical** — No backend CI at all — 117 passing JUnit tests exist and
  nothing runs them on push or PR
- [ ] **High** — No frontend unit-test gate either — the one workflow only
  runs Playwright's e2e scaffold
- [ ] **Medium** — No linting/static analysis in CI (no ESLint, Checkstyle,
  SpotBugs)
- [ ] **Medium** — No Dependabot/Renovate — dependency patching is entirely
  manual despite a completed security review
- [ ] **Low** — No staging environment — every push to `main` deploys
  straight to production on both Render and Vercel

### Observability

- [ ] **High** — No structured logging — default console appender, `INFO`
  only, nothing JSON or query-able
- [ ] **Medium** — No error tracking (Sentry or equivalent) on either backend
  or frontend — a crash today is invisible until someone notices
- [ ] **Medium** — Actuator only exposes `health`/`info` — no metrics
  endpoint, no APM, no request tracing

### Security

- [ ] **Medium** — No CAPTCHA or honeypot on the public registration form —
  deferred during the Phase 25 review, still open
- [ ] **Medium** — SePay webhook contract (field names, auth header, amount
  handling) should be verified against the real sandbox, not left as a guess
- [ ] **Low** — JWT has no revocation/blacklist — a deliberate accepted
  tradeoff (1h expiry, no refresh) worth revisiting once there's real traffic

---

## The route

Eight stops, each one a learnable skill pulled straight from the task board
above. Ordered so each stop gives you the context and tooling the next one
needs — stabilize before you test, test before you automate, automate before
you harden. Do them one at a time; treat every finished task as its own small
PR.

### 1. Stabilize &amp; orient
*Skill: reading an unfamiliar codebase fast · Angular typing · technical writing — 0.5–1 day*

- [ ] Fix `submission-detail.component.spec.ts`'s mock (add `assignedToId`) so
  `ng test` compiles again
- [ ] Trace the Admin User Management feature end-to-end (`AdminUserController`
  → service → repository → `V7` migration → Angular's assignment dropdown and
  notes thread) and update `docs/backend-specification.md`,
  `docs/ui-specification.md`, and `PAGES.md` to actually describe it

> The fastest way to get fluent in someone else's (or your own three-weeks-ago)
> code is to be forced to document it correctly. This also unblocks your own
> test suite before you touch anything else.

### 2. Testing fundamentals
*Skill: Mockito &amp; JUnit slices · Jasmine/Karma · TestBed &amp; HttpTestingController — 1–2 weeks*

- [ ] Backend: `AuthServiceTest`, `PaymentMapperTest`, unit tests for
  `JwtService`/`JwtAuthenticationFilter`/`SecurityConfig`, a `@WebMvcTest` slice
  for `SepayWebhookController`
- [ ] Frontend: real tests for `auth.guard.ts` and `auth.interceptor.ts`,
  specs for all 6 services, and turn the 7 boilerplate component specs into
  tests that assert actual behavior

> This is the single highest-leverage stop: it's the backbone every later stop
> (CI, refactors, upgrades) leans on, and it directly covers the code paths —
> auth and payments — where a silent bug costs the most.

### 3. CI/CD pipeline
*Skill: GitHub Actions · build caching · dependency bots — 3–5 days*

- [ ] Add a workflow that runs `./gradlew build test` on push/PR
- [ ] Add an `ng test` job alongside the existing Playwright workflow
- [ ] Add ESLint for Angular and Checkstyle or SpotBugs for the backend as a
  gate, not just a local habit
- [ ] Turn on Dependabot (or Renovate) for both `build.gradle` and
  `package.json`

> Stop 2's tests are only worth what enforces them. This is also where you
> learn the muscle of "a merge should be provably safe," which transfers to any
> team you join.

### 4. Observability
*Skill: structured logging · Spring Boot Actuator/Micrometer · APM concepts — 3–5 days*

- [ ] Add `logback-spring.xml` with a JSON encoder so logs are queryable, not
  just scrollable
- [ ] Wire a free-tier error tracker (e.g. Sentry) into both the backend and
  the Angular app
- [ ] Add Micrometer and expand Actuator past `health`/`info` with a metrics
  endpoint

> Right now a production exception is invisible until a user complains. This
> stop teaches you to see your own system the way an on-call engineer has to.

### 5. Security hardening
*Skill: webhook authentication patterns · token lifecycle design · abuse mitigation — 3–5 days*

- [ ] Add a honeypot field to the public registration form
- [ ] Verify the SePay webhook's real field names, auth header, and
  amount-handling contract against the sandbox, and lock the integration to
  what's actually documented
- [ ] Decide — and write down — the JWT revocation posture: implement a
  lightweight blacklist, or explicitly re-confirm the no-refresh tradeoff is
  still fine

> The existing security review (`docs/security-review.md`) already modeled
> this well; this stop is about closing its one deliberately-deferred item and
> de-risking the one integration built on guesses.

### 6. Performance &amp; data
*Skill: SQL indexing &amp; `EXPLAIN` plans · query projections · `@Scheduled` jobs — ~1 week*

- [ ] Write a `V8` migration adding indexes on `submission.status`,
  `course.license_class`/`branch`, and `submission.assigned_to_id`
- [ ] Fix the extra-`COUNT`-query pattern in course listing with a single
  projected query
- [ ] Add a `PaymentStatus.EXPIRED` state, a scheduled job to age out stale
  `PENDING` payments, and an admin action to cancel or force-approve one

> This is the first stop where you're optimizing something real instead of
> adding scaffolding — a good place to practice reading query plans before
> guessing at fixes.

### 7. Frontend architecture &amp; polish
*Skill: RxJS interceptors · Playwright authoring · accessibility auditing — 1–2 weeks*

- [ ] Pull the duplicated `extractErrorMessage` logic into one interceptor or
  service
- [ ] Replace the Playwright scaffold with a real suite: registration flow,
  admin login, assignment, payment generation
- [ ] Run an accessibility pass (aria labels, roles, focus states — try
  `axe-core` against each page) and add the missing wildcard route
- [ ] Move payment status from manual refresh to polling

> Everything here is visible to a real user or a real reviewer — the payoff
> for this stop is the most externally legible of the whole route.

### 8. Infra maturity — stretch
*Skill: environment strategy · IaC · framework upgrade discipline — open-ended*

- [ ] Stand up a staging environment (a second Render service + a Neon
  branch) so production isn't the first place a change runs
- [ ] Convert Render's manual click-through setup into a `render.yaml`
- [ ] Consolidate the scattered env-var docs into one real `.env.example`
- [ ] Evaluate an Angular 19 → latest major upgrade now that the test suite
  (stop 2) can actually catch a regression

> Optional, and deliberately last — none of it is urgent, and doing it before
> stops 2–3 exist would mean upgrading or branching a thing you can't yet
> verify.

---

## How to use this

Work top to bottom, but don't block on finishing a stop before starting the
next if a task inside it is genuinely idle (e.g. waiting on SePay sandbox
access). Each bullet under a stop is sized to be its own PR — small enough to
review in one sitting, which is also the fastest way to get fast at the
review habit itself.

Sourced from `CHECKLIST.md`, `docs/backend-specification.md`,
`docs/ui-specification.md`, `docs/architecture-diagram.md`,
`docs/security-review.md`, `docs/planning/plan-3-feature-roadmap.md`, and a
direct read of `backend/src` and `clientUI/src` at commit `fb26d53`.
