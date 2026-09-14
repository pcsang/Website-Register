# Information Collection & Admin Management System

A small system with a public form for submitting contact/inquiry information and an admin area for
reviewing and managing those submissions.

- **Backend**: Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Hibernate, Jakarta Validation,
  PostgreSQL, Maven, springdoc-openapi (Swagger UI)
- **Frontend**: Angular 19 + Angular Material, in `clientUI/` (see [`clientUI/README.md`](clientUI/README.md)
  for frontend-specific setup)

The full product spec, architecture, and phased build plan live in
[`java-spring-boot-angular-project-prompts.md`](java-spring-boot-angular-project-prompts.md). This README
only covers getting the **backend** running locally; see `clientUI/README.md` for the Angular app.

## Current status

Backend phases 1–16 of the roadmap are implemented: project setup, PostgreSQL configuration, the
`Submission` entity, the public `POST /api/submissions` endpoint, centralized API exception handling, the
admin submission list/detail/status-update endpoints, the dashboard summary endpoint, CORS for the Angular
dev server, and stateless JWT admin authentication (`POST /api/auth/login`, `/api/admin/**` protected).
Frontend phases 11–15 are also implemented (public form, admin dashboard, submission detail UI).
**Not yet implemented:** the Angular login page/auth guard (the admin UI doesn't send a token yet, even
though the API now requires one), Docker, and deployment.

## Prerequisites

- **JDK 21** ([Eclipse Temurin](https://adoptium.net/) or any other distribution)
- **PostgreSQL 14+**, running locally or via Docker
- Maven is **not required** — the project includes the Maven Wrapper (`mvnw` / `mvnw.cmd`), which downloads
  the correct Maven version automatically on first use.

> **No JDK/Maven/PostgreSQL installed system-wide?** This repo may already have a portable toolchain
> downloaded into `tools/` at the repo root (git-ignored) for exactly this situation. If `tools/jdk-*`,
> `tools/apache-maven-*`, and `tools/pgsql` exist, use those instead of installing anything — see
> **[Using the portable toolchain](#using-the-portable-toolchain-no-system-install)** below, then skip
> straight to [step 3](#3-build-and-run).

## Using the portable toolchain (no system install)

If `tools/jdk-21.0.12.1+1`, `tools/apache-maven-3.9.9`, and `tools/pgsql` exist in the repo, point your
shell at them before running any `mvn`/`mvnw` command:

```powershell
$env:JAVA_HOME = "D:\Home\Website-Register\tools\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;D:\Home\Website-Register\tools\apache-maven-3.9.9\bin;$env:Path"
```

```bash
export JAVA_HOME=/path/to/repo/tools/jdk-21.0.12.1+1
export PATH="$JAVA_HOME/bin:/path/to/repo/tools/apache-maven-3.9.9/bin:$PATH"
```

Then start the bundled PostgreSQL (data dir `tools/pgdata`, superuser `postgres`/`postgres`, database
`information_db`, port `5432` — matching this README's defaults, so step 2 can be skipped):

```powershell
tools\pgsql\bin\pg_ctl.exe -D tools\pgdata -l tools\pg.log -o "-p 5432" start
tools\pgsql\bin\pg_isready.exe -p 5432   # confirm it's up
tools\pgsql\bin\pg_ctl.exe -D tools\pgdata stop   # when done
```

If a real JDK 21 / Maven / PostgreSQL are already on `PATH` or reachable elsewhere, prefer those and ignore
this section — the portable toolchain only exists as a fallback for machines without them.

## 1. Create the database

Using a local PostgreSQL install:

```bash
createdb -U postgres information_db
```

Or with Docker (no local PostgreSQL install needed):

```bash
docker run --name information-db \
  -e POSTGRES_DB=information_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 -d postgres:16
```

## 2. Configure environment variables

The app reads its datasource config from environment variables, falling back to local-dev defaults if
unset (see `backend/src/main/resources/application.yml`):

| Variable            | Default                                            | Description                                        |
|---------------------|-----------------------------------------------------|------------------------------------------------------|
| `DB_URL`            | `jdbc:postgresql://localhost:5432/information_db`  | JDBC connection URL                                |
| `DB_USERNAME`       | `postgres`                                          | Database user                                      |
| `DB_PASSWORD`       | `postgres`                                          | Database password                                  |
| `ALLOWED_ORIGINS`   | `http://localhost:4200`                             | CORS-allowed origin for the Angular dev server     |
| `JWT_SECRET`        | a dev-only placeholder — **not** production-safe   | HMAC signing secret for admin JWTs (32+ bytes)     |
| `JWT_EXPIRATION_MS` | `3600000` (1 hour)                                  | How long an issued admin JWT stays valid           |
| `ADMIN_USERNAME`    | `admin`                                             | Seeded admin username (see note below)             |
| `ADMIN_PASSWORD`    | a dev-only placeholder — **not** production-safe   | Seeded admin password, BCrypt-hashed before storage |

**About the seeded admin account:** there's no signup endpoint (this is an internal tool). On first
startup, if the `admin_users` table is empty, the app creates exactly one admin account from
`ADMIN_USERNAME`/`ADMIN_PASSWORD` (hashed with BCrypt) — safe to leave running, it never overwrites or
duplicates an existing account. **Always set real values for `JWT_SECRET`, `ADMIN_USERNAME`, and
`ADMIN_PASSWORD` outside local development** — the defaults in `application.yml` are placeholders.

If your database matches the defaults above (as set up in step 1) and you're running the Angular dev
server on its default port, you can skip this step entirely. For anything else — a different
user/password, a remote database, a different frontend origin, a production environment — set the
relevant variables before running the app:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/information_db
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/information_db"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
```

**Never commit real credentials.** The defaults above are placeholders for local development only.

## 3. Build and run

All commands run from the `backend/` directory. Make sure PostgreSQL is running first (see step 1, or
[Using the portable toolchain](#using-the-portable-toolchain-no-system-install) if you're using the
bundled one) — the app and its tests fail fast otherwise (see [Troubleshooting](#troubleshooting)).

```bash
cd backend

# build and run the full test suite
./mvnw clean verify          # macOS/Linux
.\mvnw.cmd clean verify      # Windows PowerShell/cmd
mvn clean verify             # if Maven is already on PATH (e.g. the portable toolchain)

# start the application (listens on http://localhost:8080)
./mvnw spring-boot:run       # macOS/Linux
.\mvnw.cmd spring-boot:run   # Windows PowerShell/cmd
mvn spring-boot:run          # if Maven is already on PATH
```

On Windows, the leading `.\` is required — PowerShell doesn't run commands from the current directory
unless you spell out the relative path, even though the file is right there.

On first run, the schema (`submissions` table) is created automatically by Hibernate
(`spring.jpa.hibernate.ddl-auto: update`) — no manual migration step needed.

Alternatively, build a jar and run it directly:

```bash
./mvnw clean package -DskipTests   # or: mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

## 4. Verify it's running

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}

curl http://localhost:8080/actuator/health
# {"status":"UP"}   (reflects real database connectivity)
```

Interactive API docs (Swagger UI) are served at <http://localhost:8080/swagger-ui.html>, generated
automatically from the controllers/DTOs — a quick way to browse or try every endpoint without `curl`.

## Trying the API

**Submit a form entry (public):**

```bash
curl -i -X POST http://localhost:8080/api/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Nguyen Van A",
    "email": "a@gmail.com",
    "phone": "0901234567",
    "message": "Hello"
  }'
```

Expected: `201 Created` with the saved submission, `status` automatically set to `"NEW"`.

`fullName` is required; `email`, if provided, must be a valid address. An invalid request returns `400`
with per-field error messages:

```bash
curl -i -X POST http://localhost:8080/api/submissions \
  -H "Content-Type: application/json" \
  -d '{"fullName": "", "email": "not-an-email"}'
```

```json
{
  "status": 400,
  "message": "Validation failed",
  "errors": { "fullName": "must not be blank", "email": "must be a valid email" },
  "timestamp": "2026-09-03T11:43:21.844",
  "path": "/api/submissions"
}
```

**Admin login:**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "<ADMIN_PASSWORD value>"}'
```

Expected: `200 OK` with `{"token": "...", "username": "admin", "role": "ROLE_ADMIN"}`. Wrong credentials
return `401` with the standard error shape (same message either way, so the API never reveals whether a
username exists).

**Admin endpoints** — everything under `/api/admin/**` now requires that token as a `Bearer` header; a
missing, invalid, or expired token gets `401`:

```bash
TOKEN="<paste the token from the login response>"

# paginated, searchable, filterable list (page/size/sort are standard Spring Pageable params)
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/admin/submissions?search=nguyen&status=NEW&page=0&size=20"

# single submission by ID
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/submissions/1

# update a submission's status
curl -i -X PATCH http://localhost:8080/api/admin/submissions/1/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'

# dashboard summary counts (total, per-status, submitted today)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/dashboard/summary
```

> **Angular admin UI heads-up:** the dashboard/detail pages in `clientUI/` don't send this token yet — that
> Angular-side login/auth-guard work is Phase 17, not yet implemented — so the admin UI will show errors
> (401s) against a backend built from this branch until that phase lands.

## Running tests

```bash
cd backend
./mvnw test              # unit/integration tests only
./mvnw clean verify       # tests + build, same as CI would run
```

Tests that load the full Spring context (`BackendApplicationTests`) need a reachable PostgreSQL database,
same as running the app — make sure steps 1–2 above are done first.

## Project structure

```
backend/
  src/main/java/com/register/backend/
    controller/   REST controllers
    service/      business logic
    repository/   Spring Data JPA repositories
    entity/       JPA entities
    dto/
      request/    inbound request bodies
      response/   outbound response bodies
    mapper/       entity <-> DTO mapping
    enums/        e.g. SubmissionStatus
    exception/    centralized error handling (@RestControllerAdvice)
    config/       CorsConfig, OpenApiConfig
    security/     (reserved, for future authentication)
  src/main/resources/application.yml
```

## Troubleshooting

- **App fails to start with a connection error** — PostgreSQL isn't reachable at the configured `DB_URL`.
  Confirm it's running (`pg_isready`) and the port/credentials match your environment variables.
- **`BackendApplicationTests` fails but other tests pass** — same cause as above; that specific test opens
  a real datasource connection to validate the full application context.
- **`mvn`/`mvnw` not found, or picks up the wrong Java version** — make sure `JAVA_HOME`/`PATH` point at a
  JDK **21**; if there's no system-wide install, see
  [Using the portable toolchain](#using-the-portable-toolchain-no-system-install) above.
- **Angular app at `localhost:4200` gets CORS errors calling the API** — confirm `ALLOWED_ORIGINS` (default
  `http://localhost:4200`) matches the origin the frontend is actually served from.
- **Removed or loosened an entity field but the old column/constraint is still there** —
  `ddl-auto: update` only adds columns/tables, it never drops or relaxes them. Reconcile manually via
  `psql`, or drop the table and let Hibernate recreate it if there's no data worth keeping.
