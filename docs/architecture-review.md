# Final Architecture Review (Phase 26)

**Scope:** the whole application as it stands today — Angular 19 (`clientUI/`) → Spring Boot 3.4/Java 21
REST API (`backend/`) → PostgreSQL, deployed (per `docs/deployment/`) as Angular → Vercel, Spring Boot →
Render, PostgreSQL → Neon. Reviewed against the roadmap's Phase 26 checklist (20 areas), per that phase's
explicit instruction: **this is a small application — do not recommend microservices, Kafka, Kubernetes,
CQRS, event sourcing, Redis, or complex DDD unless there's a concrete reason.** None of those are
recommended anywhere below.

Recommendations are classified **Critical / Should Improve / Nice to Have**. This review does not
re-litigate Phase 25's security findings in detail — `docs/security-review.md` already covers areas 9, 10,
16, 17, 18 in depth and is linked from those sections here rather than duplicated.

---

## Summary table

| # | Area | Status |
|---|---|---|
| 1 | Project structure | ✅ No issue |
| 2 | REST API design | 🔵 Nice to have (1 item) |
| 3 | DTO design | ✅ No issue |
| 4 | Entity design | ✅ No issue (documented tradeoffs, still correct) |
| 5 | Validation | ✅ No issue |
| 6 | Exception handling | ✅ No issue |
| 7 | Search/filter/pagination | ✅ No issue |
| 8 | Database performance | 🔵 Nice to have (2 items) |
| 9 | Spring Security | ✅ No issue — see `docs/security-review.md` §1–2 |
| 10 | JWT implementation | 🔵 Nice to have (1 item) — see `docs/security-review.md` §4–5 |
| 11 | Angular architecture | ✅ No issue |
| 12 | Angular API services | 🔵 Nice to have (1 item) |
| 13 | Error handling | ✅ No issue |
| 14 | Docker | ✅ No issue |
| 15 | Production configuration | ✅ No issue |
| 16 | CORS | ✅ No issue — see `docs/security-review.md` §6 |
| 17 | Security (general) | ✅ No issue — see `docs/security-review.md` |
| 18 | Logging | ✅ No issue — see `docs/security-review.md` §12 |
| 19 | Testing | 🟡 Should improve (frontend coverage) |
| 20 | Deployment | 🔴 **Critical** (branch divergence) |

---

## 1. Project structure

`backend/…/com/register/backend/{config,controller,dto/{request,response},entity,enums,exception,mapper,
repository,security,service}` and `clientUI/src/app/{admin,public,core,models}` — both match what
`CLAUDE.md` documents, and every file found on disk fits the convention it's supposed to (no stray
controller-with-logic, no entity leaking past a mapper, no component reaching past its service). **No
issue.**

## 2. REST API design

Verbs and status codes are consistent and RESTful throughout (`POST` → 201, `GET` → 200, `PATCH` → 200,
proper `/api/admin/**` vs public split). One inconsistency, cosmetic only: `PATCH /api/admin/courses/{id}`
is documented as a **full replacement** of every editable field (`CourseMapper.applyUpdate` sets all of
them, no partial-update semantics) — `PUT` would be the more precise verb for that. `PATCH /api/admin/
submissions/{id}/status`, by contrast, really is a partial update (one field) and correctly uses `PATCH`.
**Nice to have** — this is an internal admin API with one frontend consumer that already calls it
correctly; not worth a breaking change for verb purity alone.

## 3. DTO design

Every request/response DTO is a `record`, validation constraints mirror the entity's DB column constraints
field-for-field (verified across `Submission`, `Course`, `AdminUser`), and no entity is ever serialized
directly — confirmed by grepping every controller/service return type. **No issue.**

## 4. Entity design

Plain `Long courseId` on `Submission` rather than a `@ManyToOne Course` association, and no JPA
relationship mapping from `Course` back to `Submission` — a deliberate simplification (documented in both
entities' Javadoc) that trades away cascade/fetch-graph convenience for avoiding lazy-loading pitfalls in a
two-entity-relationship system this small. The FK is still enforced at the database level
(`V3__extend_submissions.sql`'s `fk_submissions_course`), so referential integrity isn't actually at risk —
only object-graph navigation convenience is traded away, and nothing in the codebase needed that
convenience. No `@Version` optimistic-locking field on any entity: acceptable for a single-admin-user
system with no realistic concurrent-edit scenario. **No issue** — both are considered tradeoffs, not gaps,
and remain appropriate at this app's scale.

## 5. Validation

Jakarta Validation annotations are present on every request DTO and match the corresponding entity/DB
constraint (`@NotBlank`/`@Size` on `String` fields with a DB `length`, `@NotNull` mirroring `nullable =
false`, `@Positive`/`@PositiveOrZero` on numeric fields). Validation failures surface as a structured 400
via `GlobalExceptionHandler`. **No issue.**

## 6. Exception handling

`GlobalExceptionHandler` covers every failure mode actually reachable in this app: bean validation
failures, not-found, invalid credentials, Spring's own "no route matched," query-param type mismatches, an
invalid `sort=` field, malformed/unparsable JSON, and a catch-all that logs full detail server-side but
only ever returns a generic message to the client. Read every handler directly — none of them leak
internals. **No issue.**

## 7. Search/filter/pagination

`Pageable`-driven throughout, `spring.data.web.pageable.max-page-size: 100` caps abuse of the `size` query
param globally, and every filter (`search`/`status`/`courseId` on submissions; `licenseClass`/`branch` on
courses) is a null-safe optional predicate applied inside the JPQL query itself — no in-memory filtering of
an unbounded result set anywhere. **No issue.**

## 8. Database performance

Two low-severity observations, both proportionate to flag but not urgent at this app's realistic scale (a
single driving school's leads/courses, not a high-volume system):

- **No explicit indexes** beyond the primary keys and `admin_users.username`'s unique constraint (which
  gets one implicitly). `submissions.status`, `submissions.course_id`, `submissions.created_at`, and
  `courses.license_class`/`branch`/`start_date` are all filtered, grouped, or sorted on in the repository
  layer with no supporting index. **Nice to have** — cheap to add (a one-line `V6` migration) and would
  matter once submission/course volume grows past a few thousand rows, but isn't causing any observed
  slowness today and isn't required at current scale.
- **`CourseService.toPageResponse`** issues one extra `COUNT` query per course row to compute
  `seatsRegistered` (up to *page size*, i.e. up to 100, extra round-trips for one admin/public course-list
  request) — already called out in the code's own Javadoc as "not a hot path for this listing size." A
  single `GROUP BY courseId` query could replace all of them with one round trip. **Nice to have** — real
  but small at today's course count (a handful of rows seeded via `V5`), and the current approach keeps
  `seatsRegistered` guaranteed-fresh with the simplest possible code, which is the right tradeoff until
  course-list traffic or row count actually justifies the extra complexity.

## 9. Spring Security

Reviewed in depth in Phase 25 (`docs/security-review.md` §1–2, §9 in that doc's numbering): stateless JWT,
single `ROLE_ADMIN`, `/api/admin/**` gated, everything else public, no unnecessary
`UserDetailsService`/`AuthenticationManager` machinery. Re-verified the current `SecurityConfig` directly
for this review (now also wiring `RateLimitingFilter` ahead of `JwtAuthenticationFilter`, added since that
phase) — the filter ordering is correct and doesn't change any of that review's conclusions. **No issue.**

## 10. JWT implementation

`JwtService` uses `jjwt` 0.12.6, HS256 via `Keys.hmacShaKeyFor`, a subject + `role` claim, 1-hour default
expiration, no refresh token (an admin re-authenticates hourly — a documented, acceptable tradeoff for a
single-role internal tool with no revocation infrastructure). `application-prod.yml` now fails fast if
`JWT_SECRET` is unset (Phase 25 fix). One item not previously called out explicitly: the Angular
`AuthService` stores the token in `localStorage`, not an `httpOnly` cookie — meaning any successful XSS
would be able to read it. This app has zero `innerHTML`/`bypassSecurityTrust*` usage (confirmed in Phase
25's review) and Angular's default interpolation auto-escapes everything, so the actual exposure is low,
but it's worth naming as a conscious tradeoff rather than an unexamined default: `localStorage` was the
simpler choice given the frontend (Vercel) and backend (Render) are on different domains, and cross-domain
`httpOnly` cookies would need `SameSite=None`+`Secure`+CORS credential wiring for no real benefit here.
**Nice to have** — not worth the added complexity unless a concrete XSS vector appears.

## 11. Angular architecture

Standalone components throughout (no `NgModule` boilerplate), functional guards/interceptors (`authGuard`,
`authInterceptor`) over the older class-based patterns, signal-based reactive state in `AuthService`
(`computed(() => ...)` for `isAuthenticated`/`username` rather than manually-managed `BehaviorSubject`s).
Routing cleanly separates public (`/`, `/admin/login`) from guarded admin routes nested under
`AdminLayoutComponent`. **No issue.**

## 12. Angular API services

One service per backend domain (`SubmissionService`, `CourseService`, `DashboardService`, `AuthService`),
matching the backend's own controller split — components never call `HttpClient` directly (verified by
grep). One known, already-previously-flagged duplication: an `extractErrorMessage`-style helper is
reimplemented in ~7 components rather than shared. **Nice to have** — a small shared utility/pipe would
remove the duplication, but it's cosmetic (each copy is a few lines and behaves identically) and pre-dates
this review; not a Plan 2 regression.

## 13. Error handling

Backend: consistent `{status, message, errors, timestamp, path}` shape everywhere (§6). Frontend:
`authInterceptor` is the single global handler for 401s (admin requests only — logs out + redirects); every
other error (validation 400s, 404s, 500s) is handled per-component with its own snackbar, which is
consistent across all admin/public components and appropriate for an app this size — a global error
interceptor for non-auth errors would just relocate, not simplify, the same per-page "what message do I
show" decisions. **No issue.**

## 14. Docker

Multi-stage `backend/Dockerfile`: `gradle:8.11.1-jdk21` build stage → `eclipse-temurin:21-jre-alpine`
runtime stage (JDK never ships in the final image), runs as a non-root `spring` user, single `EXPOSE 8080`.
`docker-compose.yml` is scoped to local dev only (Postgres + backend, healthcheck-gated startup,
`.env`-overridable credentials that are obviously non-production defaults). **No issue.**

## 15. Production configuration

`application-prod.yml` correctly narrows what changes for `prod`: `show-sql`/`format_sql` off, a small
fixed Hikari pool sized for Neon's connection cap, and (since Phase 25) fail-fast `JWT_SECRET`/
`ADMIN_PASSWORD` with no dev fallback. Everything else inherits `application.yml`'s env-var-backed
defaults, which is the right amount of profile-specific override for an app this size — no unnecessary
duplication. **No issue.**

## 16. CORS

Reviewed in Phase 25 (`docs/security-review.md` §6): explicit allow-list via `ALLOWED_ORIGINS`, no
wildcard, method/header lists scoped to what the frontend actually sends. **No issue** — the same
operational note from that review still applies (Render's `ALLOWED_ORIGINS` must include the real Vercel
URL once that deployment exists — see §20 below, which is a bigger and related problem).

## 17. Security (general)

No new findings beyond Phase 25's dedicated 18-area review — see `docs/security-review.md` for the full
treatment (authentication, authorization, password hashing, sensitive data exposure, rate limiting, and
the documented CAPTCHA/spam deferral). **No issue.**

## 18. Logging

Reviewed in Phase 25 (`docs/security-review.md` §12): no password/token logging anywhere, `show-sql` is
off in `prod`, unexpected exceptions are logged server-side with full detail via SLF4J but never exposed to
the client. **No issue.**

## 19. Testing — Should improve

**Backend:** strong and appropriately layered — 93 tests as of Phase 25 (controller slice tests per
controller, service unit tests, a mapper test, `GlobalExceptionHandlerTest`, a Spring-context
`SecurityIntegrationTest`, an end-to-end `SubmissionApiIntegrationTest`, and the new
`RateLimitingFilterTest`). Hermetic (H2, `ddl-auto: create-drop`, Flyway disabled) per the `test` profile's
documented Phase 19 decision. **No issue on the backend.**

**Frontend:** every one of the 9 `.spec.ts` files is the unmodified Angular CLI scaffold — a single
`it('should create', ...)` smoke test per component, with **zero** assertions on actual behavior. Nothing
exercises `authGuard`'s redirect logic, `authInterceptor`'s token-attachment/401-logout behavior, or any
service's HTTP call shape/query-param construction — all logic that a bug could silently break with no test
catching it. **Should improve** — this isn't "add exhaustive coverage everywhere" (most components are
thin, template-heavy, and lower-value to unit test), but `auth.guard.ts` and `auth.interceptor.ts`
specifically are small, pure, security-relevant pieces of logic that are cheap to test properly (a handful
of `TestBed`/mock-based cases each) and currently have none. Recommended, not required immediately: add
real spec coverage for those two files before the next auth-related change, rather than for the whole
frontend at once.

## 20. Deployment — Critical

`docs/deployment/render-backend-deployment.md` and the Render/Vercel setup both assume deploying from
**`main`**. Checked directly: `main` has exactly one commit (`7040b50 Initial project`) — it has **never
been updated** since this repo's very first commit. Every phase of actual work (JWT auth, Angular frontend,
tests, Gradle migration, the full DriveUp domain pivot, the Phase 25 security fixes — everything currently
on `develop`, at `5aa083c`) is absent from `main`.

This is not a hypothetical risk: the CORS error investigated earlier this session (the live frontend
calling a placeholder backend URL) traced back to exactly this divergence, meaning **the live Render/Vercel
deployment has, at least at some point, been running code from `main`'s single initial commit** — i.e. none
of Phases 16 onward, none of the DriveUp redesign, and none of Phase 25's security fixes are live. This
directly affects §16 (CORS) and every security fix in `docs/security-review.md` too: a fail-fast
`JWT_SECRET` check or a rate limiter sitting on `develop` provides zero protection to a production
deployment that isn't running `develop`'s code.

**This has been surfaced twice before in this session** (as a CORS bug, and again when Plan 2 was
approved) and the user chose to defer resolving it both times, to pursue other work first. Flagging it here
formally, as this phase's job is exactly to catch this kind of "everything downstream of a config choice
looks fine, but the choice itself is wrong" issue: **before treating any of Phases 16–25 as actually
deployed/production-verified, either fast-forward `main` to `develop`'s current state, or reconfigure
Render/Vercel to deploy from `develop` directly.** No code change is proposed here — this is a
branch/deployment-configuration decision for the user to make, not something to fix unilaterally as part of
a review phase.

---

## What this review did not re-litigate

Areas 9, 10 (partially), 16, 17, 18 point back to `docs/security-review.md` rather than repeating that
review's findings — read that document for the full detail on JWT secret management, rate limiting, CORS
origin handling, and the documented CAPTCHA/spam deferral.

## Files changed by this review

None — this is a read-only review; its only output is this document.

---

## Addendum (2026-09-22): Phase 27 — SePay Payment Integration

Phase 27 shipped after this review and is not part of its original 20-area scope or summary table above —
noted here rather than folded into the table so it's clear what was and wasn't covered by the original
Phase 26 pass. See `CHECKLIST.md`'s Phase 27 entry for the full implementation log.

- **Area 2 (REST API design):** two new endpoints follow the same conventions already found "no issue"
  here — `POST`/`GET /api/admin/submissions/{id}/payment` sit under the existing `/api/admin/**` pattern,
  and the new public `POST /api/webhooks/sepay` is the first genuinely public *mutating* endpoint in the
  app (previously only `POST /api/submissions` and `POST /api/auth/login` were public + mutating, both
  already rate-limited). It authenticates itself via a manually-checked shared-secret header rather than
  Spring Security, since it's a server-to-server caller, not a browser session — a deliberate, documented
  deviation from every other endpoint's auth model, not an oversight.
- **Area 8 (Database performance):** `payments` (migration `V6`) follows the same indexing discipline as
  the areas already flagged Nice-to-have here — an explicit index was added on the lookup column
  (`submission_id`) this time, addressing the same class of gap noted for `submissions`/`courses` in the
  original review, though those original columns remain unindexed.
- **Area 19 (Testing — originally "Should improve," frontend coverage):** Phase 27 added 20 new tests (13
  backend unit, 4 backend controller-slice, 3 backend full-context integration, 6 real frontend
  assertions on the new payment UI) — a genuine data point in the right direction, but it doesn't resolve
  the original finding: the 9 pre-existing frontend `.spec.ts` scaffolds (including `auth.guard.ts`/
  `auth.interceptor.ts`) are still untested. Still **Should improve**, unchanged.
- **Area 20 (Deployment — Critical, branch divergence):** unresolved, and now has one more consequence:
  five new `SEPAY_*` environment variables (see `docs/deployment/sepay-payment-workflow.md`) must be set on
  whichever branch/environment Render is *actually* running — if that's still `main` per this review's
  original finding, the payment feature wouldn't just be insecure, it would be entirely non-functional
  (blank bank details, placeholder webhook secret). This is additional evidence for the same
  already-Critical finding, not a new one — the underlying issue and recommended resolution are unchanged.
- **New area not in the original 20 — anti-fraud control:** worth recording even though it wasn't one of
  the original review's checklist items: the payment webhook's `paymentCode` is a guessable sequential
  value (by design — see Phase 27's log), so `PaymentService.handleSepayWebhook` enforces a 90%-of-expected-
  amount floor before auto-marking a payment paid, specifically to prevent a trivial real transfer with a
  guessed/observed code from faking a much larger payment. Added after an independent review of Phase 27
  itself (not this document) flagged the gap; recorded here for anyone auditing payment-handling logic
  later without re-reading the full Phase 27 checklist log.

No code changed by this addendum — like the review it extends, it's a documentation-only update.
