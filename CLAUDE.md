# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

An "Information Collection & Admin Management System": a public form where users submit contact/inquiry
information, and an admin area (dashboard, list, detail, status update) to manage those submissions.

The full product spec, tech stack, target architecture, package structure, API design, and — most
importantly — the **phased implementation roadmap** live in
[`java-spring-boot-angular-project-prompts.md`](java-spring-boot-angular-project-prompts.md) at the repo
root. That file is the source of truth for "what comes next" and for the working rules below. Read it
before planning any non-trivial change; this CLAUDE.md summarizes and operationalizes it, but does not
replace it.

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

Backend phases 1–5 of the roadmap are implemented: project init, PostgreSQL config, `Submission` entity,
`POST /api/submissions`, and centralized exception handling. No Angular frontend exists yet. No
authentication exists yet. Check `java-spring-boot-angular-project-prompts.md`'s "PHASE" headers against
the codebase to see what's next (Phase 6 — admin submission list — is the likely next step).

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

`spring.jpa.hibernate.ddl-auto` is `update`. Hibernate schema-update only *adds* tables/columns and widens
column types — it never drops a column or relaxes a `NOT NULL` constraint when an entity field is removed
or loosened. After removing/loosening an entity field, manually reconcile the table via `psql` (or drop and
let Hibernate recreate it, if there's no data worth keeping yet).

## Architecture

Layered, Controller → Service → Repository, one JPA entity so far (`Submission`). Package structure under
`com.register.backend` (`backend/src/main/java/com/register/backend/`):

```
config/       empty so far
controller/   thin REST controllers — validate input, delegate to a service, map to a response DTO
dto/request/  inbound request bodies (Jakarta Validation annotations live here)
dto/response/ outbound response bodies — the only shapes the API ever returns
entity/       JPA entities
enums/        e.g. SubmissionStatus (NEW, IN_PROGRESS, COMPLETED)
exception/    ResourceNotFoundException, ErrorResponse, GlobalExceptionHandler (@RestControllerAdvice)
mapper/       manual entity <-> DTO mapping (no MapStruct/ModelMapper — kept intentionally simple)
repository/   Spring Data JpaRepository interfaces
security/     empty so far — reserved for Phase 16 (JWT auth)
service/      business logic, @Transactional boundaries
```

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
