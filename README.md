# Information Collection & Admin Management System

A small system with a public form for submitting contact/inquiry information and an admin area for
reviewing and managing those submissions.

- **Backend**: Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Hibernate, Jakarta Validation,
  PostgreSQL, Maven
- **Frontend**: Angular (not implemented yet)

The full product spec, architecture, and phased build plan live in
[`java-spring-boot-angular-project-prompts.md`](java-spring-boot-angular-project-prompts.md). This README
only covers getting the backend running locally.

## Current status

Implemented so far: project setup, PostgreSQL configuration, the `Submission` entity, `POST
/api/submissions`, and centralized API exception handling. No admin API, authentication, or frontend yet.

## Prerequisites

- **JDK 21** ([Eclipse Temurin](https://adoptium.net/) or any other distribution)
- **PostgreSQL 14+**, running locally or via Docker
- Maven is **not required** — the project includes the Maven Wrapper (`mvnw` / `mvnw.cmd`), which downloads
  the correct Maven version automatically on first use.

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

| Variable      | Default                                        | Description         |
|---------------|-------------------------------------------------|----------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/information_db` | JDBC connection URL |
| `DB_USERNAME` | `postgres`                                       | Database user        |
| `DB_PASSWORD` | `postgres`                                       | Database password    |

If your database matches the defaults above (as set up in step 1), you can skip this step entirely. For
anything else — a different user/password, a remote database, a production environment — set the three
variables before running the app:

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

All commands run from the `backend/` directory.

```bash
cd backend

# build and run the full test suite
./mvnw clean verify        # macOS/Linux
mvnw.cmd clean verify       # Windows

# start the application (listens on http://localhost:8080)
./mvnw spring-boot:run      # macOS/Linux
mvnw.cmd spring-boot:run    # Windows
```

On first run, the schema (`submissions` table) is created automatically by Hibernate
(`spring.jpa.hibernate.ddl-auto: update`) — no manual migration step needed.

Alternatively, build a jar and run it directly:

```bash
./mvnw clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

## 4. Verify it's running

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}

curl http://localhost:8080/actuator/health
# {"status":"UP"}   (reflects real database connectivity)
```

## Trying the API

**Submit a form entry:**

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
    config/       (reserved)
    security/     (reserved, for future authentication)
  src/main/resources/application.yml
```

## Troubleshooting

- **App fails to start with a connection error** — PostgreSQL isn't reachable at the configured `DB_URL`.
  Confirm it's running (`pg_isready`) and the port/credentials match your environment variables.
- **`BackendApplicationTests` fails but other tests pass** — same cause as above; that specific test opens
  a real datasource connection to validate the full application context.
