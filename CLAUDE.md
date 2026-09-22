# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Started as a generic "Information Collection & Admin Management System" (public submission form + admin
dashboard/list/detail/status-update). Since Plan 2 (see `docs/planning/plan-2-full-redesign-driveup.md`)
it's been re-skinned and domain-adopted into **"DriveUp"**, a Vietnamese driving-school registration/admin
platform: a public landing page with a course catalog + registration form, and an admin area
(Overview/Students/Courses + submission detail) to manage registrations, courses, and — as of Phase 27 —
VietQR/SePay tuition payments. The underlying `Submission`/`Course` domain and layered architecture are
unchanged by the rebrand; see `PAGES.md` for what's on every page today.

The original phased roadmap lives in
[`java-spring-boot-angular-project-prompts.md`](java-spring-boot-angular-project-prompts.md) at the repo
root — useful for historical rationale on early decisions, but it predates the DriveUp rebrand and Phases
24–27, so treat it as background, not as "what comes next." **For the actual current state of the code**,
prefer these living docs over the roadmap doc or this file's own memory of past sessions:
- [`docs/architecture-diagram.md`](docs/architecture-diagram.md) — one system diagram (frontend → backend
  → database, plus the SePay/VietQR payment flow) for the whole stack at a glance.
- [`docs/backend-specification.md`](docs/backend-specification.md) — current backend implementation
  (entities, endpoints, config), re-derived from source, not from the roadmap.
- [`docs/ui-specification.md`](docs/ui-specification.md) — current Angular implementation.
- [`PAGES.md`](PAGES.md) — what's on every page today, from a user-facing angle.
- [`CHECKLIST.md`](CHECKLIST.md) — phase-by-phase log of what was actually built and why, including
  everything built outside the original numbered roadmap (the DriveUp redesign, Phase 27 SePay payments).

This CLAUDE.md's job is narrower: the working rules below, and the local environment/commands section —
not a restatement of current implementation state, which drifts too fast to keep duplicated here.

## Working rules (from the roadmap's "MASTER PROMPT FOR EVERY CODING STEP")

These are standing instructions for every task in this repo, not just the original phased rollout:

- Inspect the existing code relevant to the task before writing anything.
- Implement **only** the current task. Do not implement future roadmap phases or unrelated features "while
  you're in there."
- Do not refactor unrelated code.
- Follow existing naming conventions and package layout (below).
- Do not introduce dependencies beyond what the task needs.
- Prefer the simplest solution that satisfies the requirement.
- Constructor injection only (no field/setter `@Autowired`).
- Never expose JPA entities through the REST API — always map to/from DTOs.
- Controllers stay thin: deserialize/validate → call one service method → map to a response. No business
  logic or repository calls in controllers.
- Business logic lives in `service/`; database access lives in `repository/`.
- Use `@Transactional` on service methods that mutate state.
- Never hardcode passwords, secrets, or production credentials — use environment variables.
- After implementing: list files created/modified, build the project, run the relevant tests, fix any
  failures your change caused, and explain how to manually verify the feature. Don't cascade into the next
  roadmap phase automatically — stop and let the next task be scoped explicitly.

## Current status

The numbered roadmap (Phases 1–26) plus an out-of-roadmap Phase 27 are all implemented and live: backend
MVP, JWT admin auth, backend/integration tests, Dockerized + deployed to Render, Angular frontend deployed
to Vercel, a full DriveUp UI/UX redesign (public landing page + course catalog + admin Overview/Students/
Courses), a production security review, a final architecture review, and — most recently — SePay VietQR
payment integration (Phase 27: generate a payment QR for a submission's tuition, a webhook marks it paid
when the bank transfer lands). Live: `https://website-register-roan.vercel.app` (frontend),
`https://backed-website-register.onrender.com` (backend).

**Don't infer "what's next" from this section** — it goes stale the moment new work lands and won't be
kept in lockstep with every future change. For the authoritative current state, read `CHECKLIST.md` (full
phase-by-phase log, newest entries at the bottom of each numbered section) and the living specs listed
above. If the user asks for a new feature not in `CHECKLIST.md`, treat it as new work to scope, not a
roadmap phase to look up.

## Commands

All commands assume `cd backend` first (the Spring Boot project lives in `backend/`, not the repo root).

**⚠️ This machine has no system-wide JDK 21 or PostgreSQL.** A portable JDK was downloaded into `tools/`
at the repo root (git-ignored) specifically to work around that. Before running any `gradlew` command, set
this in the same shell:

```powershell
$env:JAVA_HOME = "D:\Home\Website-Register\tools\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

If a later session finds a real JDK 21 on `PATH` already (e.g. the user installed one), prefer that and
skip this. The project's own `gradlew`/`gradlew.bat` wrapper self-bootstraps its pinned Gradle version on
first run (needs network access to `services.gradle.org` once; cached under `~/.gradle` after that) — no
separate Gradle install is needed either way.

**Build tool: Gradle, not Maven** (migrated from Maven — `pom.xml`/`mvnw`/`.mvn/` no longer exist).
`tools/apache-maven-3.9.9` and `tools/gradle-8.11.1` are leftover one-time bootstrap copies (git-ignored);
`tools/gradle-8.11.1` is kept as a fallback re-seed source in case this sandbox's proxy ever blocks a fresh
`./gradlew` distribution download again (see the migration's `CHECKLIST.md` log entry for why), Maven's copy
is no longer needed for anything.

```powershell
# build + run all tests
.\gradlew clean build

# run a single test class
.\gradlew test --tests "com.register.backend.service.SubmissionServiceTest"

# package only, skip tests
.\gradlew clean bootJar -x test

# run the app (needs PostgreSQL reachable — see below)
.\gradlew bootRun
# or, after packaging:
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

### Local PostgreSQL

Also portable (`tools/pgsql`, data dir `tools/pgdata`), initialized with superuser `postgres` / password
`postgres`, database `information_db`, listening on `5432` — matching `application.yml`'s defaults.

```powershell
# start
tools\pgsql\bin\pg_ctl.exe -D tools\pgdata -l tools\pg.log -o "-p 5432" start

# check it's up
tools\pgsql\bin\pg_isready.exe -p 5432

# stop
tools\pgsql\bin\pg_ctl.exe -D tools\pgdata stop

# psql shell
$env:PGPASSWORD = "postgres"
tools\pgsql\bin\psql.exe -U postgres -h localhost -p 5432 -d information_db
```

If a real local/Docker PostgreSQL is available instead, just point `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` at
it (see `application.yml`) — nothing in the app depends on the portable one specifically.

### Schema caveat

As of Phase 22, Flyway owns the schema (`backend/src/main/resources/db/migration/`) and
`spring.jpa.hibernate.ddl-auto` is `validate` — Hibernate only checks the entities match the database, it
never alters it. After changing an entity's mapping, add a new `V{n}__description.sql` migration (never
edit an already-applied one) rather than relying on Hibernate to adjust the table. The `test` profile (H2,
`application-test.yml`) is the one exception — it disables Flyway and keeps `ddl-auto: create-drop`,
generating its schema straight from the entities each run, per Phase 19's hermetic-test decision.

## Architecture

Layered, Controller → Service → Repository, now several JPA entities (`Submission`, `AdminUser`, `Course`,
`DashboardSettings`, `Payment`). Package structure under `com.register.backend`
(`backend/src/main/java/com/register/backend/`) — see
[`docs/backend-specification.md`](docs/backend-specification.md) for the full, current, per-file detail
(every entity's fields, every endpoint, every config property); this is just the shape:

```
config/       CORS policy, OpenAPI/Swagger metadata, AdminUserSeeder (startup admin seeding)
controller/   thin REST controllers — validate input, delegate to a service, map to a response DTO
dto/request/  inbound request bodies (Jakarta Validation annotations live here)
dto/response/ outbound response bodies — the only shapes the API ever returns
entity/       JPA entities
enums/        SubmissionStatus, LicenseClass, CourseAvailabilityStatus, PaymentStatus
exception/    ResourceNotFoundException, ErrorResponse, GlobalExceptionHandler (@RestControllerAdvice)
mapper/       manual entity <-> DTO mapping (no MapStruct/ModelMapper — kept intentionally simple)
repository/   Spring Data JpaRepository interfaces
security/     Spring Security 6 JWT config, JwtService, JwtAuthenticationFilter, RateLimitingFilter
service/      business logic, @Transactional boundaries
```

The Angular frontend (`clientUI/src/`) has its own equivalent living spec:
[`docs/ui-specification.md`](docs/ui-specification.md).

Key conventions established so far, worth matching in new code:

- DTOs are Java `record`s.
- Entity timestamps (`createdAt`/`updatedAt`) are managed via `@PrePersist`/`@PreUpdate` directly on the
  entity, not Spring Data JPA auditing — deliberate choice for a small, single-entity project; revisit if
  more entities need the same behavior or `@CreatedBy`/`@LastModifiedBy` becomes necessary.
  DB columns are `NOT NULL` **and** the same field usually also carries the matching Jakarta Validation
  annotation on the DTO — the DB constraint is the real guarantee, the DTO annotation gives a clean 400
  instead of a raw SQL error.
- Global error shape (`GlobalExceptionHandler`) is `{status, message, errors, timestamp, path}`, `errors`
  omitted (`@JsonInclude(NON_NULL)`) unless there are field-level validation errors. Unexpected exceptions
  are logged server-side with full detail via SLF4J but only ever return a generic message to the client —
  never leak a stack trace or exception message from an unhandled `Exception`.
- `NoResourceFoundException` (Spring's "no route matched") has its own handler mapped to 404 — don't let a
  broad `catch (Exception)`-style handler swallow it into a false 500.
