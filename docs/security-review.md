# Production Security Review (Phase 25)

**Scope:** the live stack — Angular (Vercel, not yet deployed) / Spring Boot + Spring Security + JWT
(Render, live at `https://backed-website-register.onrender.com`) / PostgreSQL (Neon, production; local
Postgres, dev). Reviewed against the roadmap's Phase 25 checklist (18 areas), per that phase's explicit
instruction: **do not over-engineer — focus on realistic risks for a small public web application.**

Findings are classified **CRITICAL / HIGH / MEDIUM / LOW / NICE TO HAVE**. Two findings were fixed as part
of this review (both small, proportionate); everything else is either already adequate for this app's size
or documented as a deliberate deferral with reasoning.

---

## Summary table

| # | Area | Status | Severity if unaddressed |
|---|---|---|---|
| 1 | Authentication | ✅ Adequate | — |
| 2 | Authorization | ✅ Adequate | — |
| 3 | Password hashing | ✅ Adequate | — |
| 4 | JWT secret management | ⚠️ **Fixed this review** | HIGH |
| 5 | JWT expiration | ✅ Adequate | — |
| 6 | CORS | ✅ Adequate | — |
| 7 | Input validation | ✅ Adequate | — |
| 8 | SQL injection | ✅ Adequate | — |
| 9 | XSS | ✅ Adequate | — |
| 10 | Sensitive data exposure | ✅ Adequate | — |
| 11 | Error responses | ✅ Adequate | — |
| 12 | Logging | ✅ Adequate | — |
| 13 | HTTPS | ✅ Adequate (platform-provided) | — |
| 14 | Database credentials | ✅ Adequate | — |
| 15 | Environment variables | ⚠️ **Fixed this review** (same root cause as #4) | HIGH |
| 16 | Admin endpoints | ✅ Adequate | — |
| 17 | Rate limiting (public submission endpoint) | ⚠️ **Fixed this review** | MEDIUM |
| 18 | Spam/bot submissions | 📋 Documented, not implemented | LOW–MEDIUM |

---

## 1. Authentication

Stateless JWT via `POST /api/auth/login`: `AdminUserRepository.findByUsername` → `BCryptPasswordEncoder.
matches()` → `JwtService.generateToken()`. Username-not-found and wrong-password both throw the identical
`InvalidCredentialsException`/message, so the API never reveals whether a username exists (standard
enumeration-prevention practice). **No issue** — appropriately simple for a single-role admin tool.

## 2. Authorization

`SecurityConfig`: `/api/admin/**` requires `hasRole("ADMIN")`, everything else `permitAll()`. Only one
role exists in the system, so there's no privilege-escalation surface between roles to worry about.
**No issue.**

## 3. Password hashing

`BCryptPasswordEncoder` (industry-standard, adaptive cost) — both the seeded admin account
(`AdminUserSeeder`) and login verification use the same encoder bean. No plaintext password is ever
persisted or logged (verified — see [§12](#12-logging)). **No issue.**

## 4. JWT secret management — **HIGH, fixed this review**

**Finding:** `application.yml` binds `app.jwt.secret` as `${JWT_SECRET:dev-only-insecure-jwt-signing-secret-...}`
— a fallback that applies in *every* profile, including `prod`. If `JWT_SECRET` were ever left unset on
Render, the app would start successfully and silently sign every admin JWT with a secret that's
plaintext-visible in this repository's git history — anyone who's ever seen the repo could forge a valid
`ROLE_ADMIN` token. **Verified this is not currently exploitable**: confirmed with the user directly that
`JWT_SECRET` is in fact set to a real value on Render (a forged-token test against the live endpoint was
attempted to verify this directly and independently, but was blocked by this session's own safety
controls before it ran — appropriately so, since sending a forged auth token at a live production system
is indistinguishable from an actual attack attempt regardless of intent). The user's direct confirmation is
the basis for treating this as not currently live-exploitable.

**Fix applied:** `application-prod.yml` now overrides `app.jwt.secret` as `${JWT_SECRET}` — **no
fallback**. If `JWT_SECRET` is unset while `SPRING_PROFILES_ACTIVE=prod`, Spring now fails to start with a
loud, immediate error instead of silently running with a known-weak secret. Local dev / the `default`
profile is unaffected (still has its fallback, for convenience).

## 5. JWT expiration

`app.jwt.expiration-ms`, default 1 hour (`3600000`). No refresh token — an admin re-authenticates hourly.
Reasonable for a small admin tool; no server-side revocation exists (documented tradeoff since Phase 16 —
see `docs/backend-specification.md` §12), acceptable given the short expiry window limits the blast radius
of a leaked token. **No issue for this app's size.**

## 6. CORS

`CorsConfig`: explicit `ALLOWED_ORIGINS` env var, **no wildcard**, methods restricted to what the app
actually uses (`GET, POST, PATCH, OPTIONS`), `allowCredentials` unset (bearer-token auth doesn't need it).
**Operational note, not a code defect:** once Vercel is deployed, `ALLOWED_ORIGINS` on Render must include
that real Vercel URL (already documented in `docs/deployment/vercel-frontend-deployment.md` §5) — flagging
here so it isn't missed as part of "production readiness," not a new finding.

## 7. Input validation

Every request DTO carries Jakarta Validation constraints matching the corresponding entity's DB column
constraints exactly (project-wide convention, verified across `Submission`, `Course`, `AdminUser` DTOs).
Validation failures return `400` with per-field messages via `GlobalExceptionHandler`. **No issue.**

## 8. SQL injection

All queries are parameterized — Spring Data derived queries, JPQL `@Query`s with named `:param` bindings
(`SubmissionRepository.search`, `CourseRepository.search`), and the one native SQL query
(`SubmissionRepository.countRegistrationsByMonthSince`) also uses a named `@Param` binding, not string
concatenation. Verified directly by reading every `@Query` in the codebase. **No issue.**

## 9. XSS

Backend is a pure JSON API (no server-rendered HTML). Frontend: grepped the entire `clientUI/src/app` tree
for `innerHTML`/`bypassSecurityTrust*` — **zero matches**. All user-submitted content (names, messages,
etc.) is rendered exclusively via Angular's default interpolation (`{{ }}`), which auto-escapes. **No
issue.**

## 10. Sensitive data exposure

`AdminUser.passwordHash` is never serialized — `LoginResponse`/every admin-facing DTO only ever exposes
`token`/`username`/`role`. JPA entities are never returned directly from any controller (project-wide DTO
convention, verified). `JWT_SECRET`/`ADMIN_PASSWORD`/`DB_PASSWORD` are env-var-only, never logged. **No
issue.**

## 11. Error responses

`GlobalExceptionHandler`'s catch-all for unexpected exceptions logs full detail server-side (SLF4J,
`log.error`) but returns only a generic `"An unexpected error occurred"` to the client — no stack trace,
no exception message, ever leaked. Verified this is the actual behavior (not just the intent) by reading
the handler directly. **No issue.**

## 12. Logging

Grepped for any log statement mentioning `password`/`token` — none found. `show-sql: true` (default
profile only — `prod` sets `show-sql: false`) logs SQL statement text, not bound parameter *values*
(Hibernate's default `show-sql` doesn't include bind values at INFO level), so this doesn't leak submitted
data into logs even in non-prod environments. **No issue.**

## 13. HTTPS

Both Render and Vercel terminate TLS automatically for their default domains — the app doesn't need to
implement this itself. Verified directly: the live backend's response headers already include
`strict-transport-security: max-age=31536000 ; includeSubDomains`, `x-content-type-options: nosniff`,
`x-frame-options: DENY` (Spring Security's default header set, active without any extra configuration).
**No issue** — no code change needed; these headers were already present before this review, not added by
it.

## 14. Database credentials

`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` — env-var-only in every environment (local dev default is the
obviously-fake `postgres`/`postgres`; production is Neon's real connection string with `?sslmode=require`,
never committed). **No issue.**

## 15. Environment variables — see [§4](#4-jwt-secret-management--high-fixed-this-review)

Same root cause and same fix as §4, extended to `ADMIN_PASSWORD` (`application-prod.yml` now also requires
`${ADMIN_PASSWORD}` with no fallback in the `prod` profile) — an unset `ADMIN_PASSWORD` in production would
otherwise seed the seeded admin account with a password that's also plaintext-visible in git history.
`ADMIN_USERNAME`/`JWT_EXPIRATION_MS` were deliberately **left with their dev-only fallbacks** even in
`prod` — a wrong-but-harmless username default and a wrong-but-harmless expiration default aren't
security-sensitive the way a weak secret/password is; forcing every env var to be explicitly set would be
exactly the over-engineering this phase's own instructions warn against.

## 16. Admin endpoints

Every `/api/admin/**` route (submissions, courses, dashboard) requires `ROLE_ADMIN` — verified this covers
every controller added across Plan 2 automatically via the existing prefix-based rule, no route was missed
(confirmed during the Plan 2 code review). **No issue.**

## 17. Rate limiting for the public submission endpoint — **MEDIUM, fixed this review**

**Finding:** `POST /api/submissions` and `POST /api/auth/login` had **zero** rate limiting — either could
be called an unlimited number of times per second by anyone. For `/api/submissions`, this is a
junk-data/volume concern (see [§18](#18-spambot-submissions)); for `/api/auth/login`, it's a genuine
unlimited-attempts brute-force surface against the single admin account.

**Fix applied:** a new in-memory, per-client-IP rate limiter (`RateLimitingFilter`), applied to just these
two endpoints, configurable via env vars, correctly reading `X-Forwarded-For` to identify the real client
IP behind Render's reverse proxy (not just `getRemoteAddr()`, which would return the proxy's IP for every
request in production). Returns `429 Too Many Requests` in the app's standard error shape once exceeded.
Deliberately **in-memory, not Redis/a distributed store** — this app runs a single instance, and the
roadmap's own instruction is not to over-engineer; an in-memory limiter is the proportionate choice here
and would need revisiting only if the app ever scales to multiple instances.

## 18. Spam/bot submissions

**Not implemented — documented recommendation only.** Rate limiting (§17) raises the cost of naive
automated spam but doesn't stop a determined bot. A real CAPTCHA (reCAPTCHA/hCaptcha) would require:
registering for an external service and obtaining API keys (outside this environment's ability to do —
it's an account the user would need to create), plus a new frontend dependency and a backend verification
call. Given this app's actual size and threat profile (an internal-facing driving-school lead-capture
form, not a high-value target), and the phase's explicit "don't over-engineer" instruction, this is
classified **LOW–MEDIUM** rather than something to build now. **Recommendation for later, if spam becomes
an actual observed problem** (not preemptively): add a honeypot field first (a hidden form field real
users never fill in but bots often do — zero external dependencies, catches unsophisticated bots) before
reaching for a full CAPTCHA integration.

---

## Files changed by this review

- `backend/src/main/resources/application-prod.yml` — fail-fast `JWT_SECRET`/`ADMIN_PASSWORD` (no
  fallback in the `prod` profile).
- `backend/src/main/java/com/register/backend/security/RateLimitingFilter.java` (new) — in-memory rate
  limiting for `POST /api/submissions` and `POST /api/auth/login`.
- `backend/src/test/java/com/register/backend/security/RateLimitingFilterTest.java` (new) — 5 unit tests
  covering the limiter directly (under-limit pass-through, 429 on the (limit+1)th request, per-IP
  isolation, `X-Forwarded-For` handling, non-rate-limited requests always passing through).
- `backend/src/main/java/com/register/backend/security/SecurityConfig.java` — wires in the new filter
  ahead of `JwtAuthenticationFilter`.
- `backend/src/main/resources/application.yml` — new `app.rate-limit.submissions-per-minute` /
  `app.rate-limit.login-attempts-per-minute` config (defaults 5 and 10).
- `backend/src/test/resources/application-test.yml` — overrides both rate limits to 1000 for the `test`
  profile only, so the existing integration test suites (which log in / submit repeatedly within one
  shared Spring context) don't trip the limiter themselves; doesn't touch the filter's actual logic.

Verified independently (not just the implementing agent's self-report): full `./gradlew clean build`
(93/93 tests pass), and a live curl-loop against a freshly booted local instance — 8 rapid
`POST /api/submissions` → first 5 `201`, next 3 `429`; 13 rapid `POST /api/auth/login` (wrong password) →
first 10 `401`, next 3 `429`; a request carrying a different `X-Forwarded-For` IP succeeded normally while
the original IP was still rate-limited, confirming per-IP isolation. Test data created during verification
was deleted afterward and the test backend instance was stopped.

See `CHECKLIST.md`'s Phase 25 entry for the full narrative and the JWT-secret fix's own verification.
