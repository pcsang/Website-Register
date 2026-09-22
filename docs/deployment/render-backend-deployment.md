# Deploying the Backend to Render (Phase 23)

Guide for taking the Spring Boot backend (`backend/`) from "builds locally" to a live service at
`https://<your-service>.onrender.com`, backed by a Neon PostgreSQL database:

```
GitHub  →  Render  →  Spring Boot (Docker)  →  Neon PostgreSQL
```

This is a **manual, one-time walkthrough** — it deploys nothing on your behalf. You'll need your own
Render and Neon accounts; this doc tells you exactly what to click and what to type.

---

## 0. Before you start

**One code change was required and has already been made**: `server.port` in
`backend/src/main/resources/application.yml` now reads `${PORT:8080}` instead of a hardcoded `8080`.
Render assigns your service a port dynamically via the `PORT` environment variable at runtime — without
this, the app would always listen on 8080 regardless of what Render actually routes traffic to, and the
deployment would fail health checks. Locally, Docker Compose, etc., nothing changes — `PORT` is unset
there, so it falls back to `8080` exactly as before. Verified: `./gradlew clean build` — 48/48 tests still
pass after this change.

**What's already in place from earlier phases** (so this guide doesn't have to re-explain them):
- `backend/Dockerfile` — multi-stage (Gradle+JDK 21 build stage, JRE-21-alpine runtime stage), runs as a
  non-root user, `EXPOSE 8080` (Phase 20).
- `backend/src/main/resources/application-prod.yml` — a `prod` Spring profile: SQL logging off,
  `open-in-view` off, a small Hikari connection pool (5) sized for Neon's free-tier connection limits
  (Phase 22).
- `backend/src/main/resources/db/migration/V1__init_schema.sql` — Flyway owns the schema;
  `ddl-auto: validate` means Hibernate only checks the schema matches, never alters it (Phase 22).
- Two health endpoints already exist: `GET /api/health` (simple custom liveness check) and
  `/actuator/health` (reflects real datasource connectivity) — satisfies requirement 9 with no new code.

**What you'll need before starting:**
- This repo pushed to GitHub (Render deploys by connecting to a GitHub repo).
- A [Render](https://render.com) account (free tier is enough to follow this guide).
- A [Neon](https://neon.tech) account (free tier is enough).

---

## 1. Create the Neon PostgreSQL database

1. Sign in to Neon, create a new project (any name/region).
2. Neon creates a default database and gives you a **connection string** on the project dashboard —
   something like:
   ```
   postgresql://<user>:<password>@<host>.neon.tech/<database>?sslmode=require
   ```
3. You'll paste this (converted to a JDBC URL, see below) as Render's `DB_URL`. Keep the tab open, or copy
   the pieces somewhere safe — **never into a file this repo tracks**.
4. **JDBC conversion**: the app needs a `jdbc:postgresql://` URL, not the raw `postgresql://` connection
   string Neon shows you. Convert it to:
   ```
   jdbc:postgresql://<host>.neon.tech/<database>?sslmode=require
   ```
   (same host/database/query string, just swap the scheme and move user/password out — they go in
   `DB_USERNAME`/`DB_PASSWORD` separately, not embedded in the URL).
5. **SSL**: no extra configuration needed on the app side — `?sslmode=require` is honored automatically by
   the PostgreSQL JDBC driver once it's part of `DB_URL`.
6. **Schema**: you don't need to run any SQL yourself. On first boot against this empty database, Flyway
   applies `V1__init_schema.sql` automatically (creating `submissions` and `admin_users`), and
   `AdminUserSeeder` creates the first admin account from `ADMIN_USERNAME`/`ADMIN_PASSWORD` (see step 3)
   since the table starts empty.

---

## 2. Create the Render web service

1. From the Render dashboard: **New → Web Service**.
2. Connect your GitHub account/repo if you haven't already, and select this repository.
3. Render will offer to auto-detect a runtime — choose **Docker**. Set:
   - **Root Directory**: `backend` (the Dockerfile and Gradle project live there, not the repo root).
   - **Dockerfile Path**: `backend/Dockerfile` (or just `Dockerfile` if Root Directory is already `backend`
     — Render's UI will show you which form it expects).
   - **Branch**: whichever branch you want live (e.g. `main`).
4. Instance type: the free tier works for this app (it's a small Spring Boot service). Note free-tier
   services **spin down after ~15 minutes of inactivity** and take 30-60s to cold-start on the next
   request — expected behavior, not a bug, if your first request after idle time is slow.

---

## 3. Environment variables (Render build/start configuration)

Because the Dockerfile's `ENTRYPOINT` is `["java", "-jar", "app.jar"]`, **Render needs no separate Build
Command or Start Command for a Docker-runtime service** — it builds the image from your Dockerfile and
runs the container's own `ENTRYPOINT` as-is. The only configuration Render needs from you is environment
variables, set on the service's **Environment** tab (stored encrypted by Render, never committed to this
repo — satisfies "do not commit secrets"):

| Variable | Value | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<host>.neon.tech/<database>?sslmode=require` | From step 1 |
| `DB_USERNAME` | your Neon role/username | From step 1 |
| `DB_PASSWORD` | your Neon password | From step 1 — **generate/copy, never reuse a dev password** |
| `JWT_SECRET` | a random string, 32+ bytes | e.g. `openssl rand -base64 48` — must **not** be the
`dev-only-...` placeholder baked into `application.yml`'s default |
| `ALLOWED_ORIGINS` | your frontend's real origin | See note below — placeholder until Phase 24 |
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates `application-prod.yml` (quieter logging, tuned pool) |
| `ADMIN_USERNAME` | your real admin username | Seeds the first admin account on first boot |
| `ADMIN_PASSWORD` | a strong real password | Seeds the first admin account on first boot |
| `JWT_EXPIRATION_MS` | e.g. `3600000` (1 hour) | Optional — falls back to 1 hour if unset |

**SePay payment env vars (Phase 27) — not covered by this guide.** If the deployed backend needs to
generate real VietQR payments (not just the core registration/admin flow this guide covers), five more
`SEPAY_*` variables are required — see
[`docs/deployment/sepay-payment-workflow.md`](sepay-payment-workflow.md) for what they are and how to get
the values from your SePay dashboard. `SEPAY_WEBHOOK_SECRET` in particular is **required** once
`SPRING_PROFILES_ACTIVE=prod` is set (same fail-fast pattern as `JWT_SECRET`/`ADMIN_PASSWORD` below) — the
app won't start without it.

**`ALLOWED_ORIGINS` note:** this is the Angular frontend's origin (for CORS), which doesn't exist yet until
Phase 24 (Vercel deploy). Set it to whatever you have now (e.g. your eventual Vercel URL if you already
know it, or leave the `application.yml` default) and **come back and update it** once the frontend is
live — then trigger a redeploy (env var changes require a restart to take effect; see step 5).

**Do not** set any of these in `application.yml`, `application-prod.yml`, or anywhere else in the repo —
they must only ever exist in Render's environment variable store and your own local shell/`.env` (already
`.gitignore`d).

---

## 4. Health check

In the service's **Settings → Health Check Path**, set:

```
/actuator/health
```

This endpoint reflects real database connectivity (not just "the JVM is up"), so Render will correctly
treat a database-connection failure as an unhealthy deploy rather than a false-positive success. (`/api/health`
also exists and always returns `{"status":"UP"}` unconditionally if you'd rather use the simpler one — but
`/actuator/health` gives Render a more meaningful signal.)

---

## 5. Deploy

Click **Create Web Service** (first time) or **Manual Deploy → Deploy latest commit** (subsequent
deploys). Render will:

1. Pull your repo at the selected branch/commit.
2. Build the Docker image from `backend/Dockerfile` (multi-stage: Gradle+JDK21 build stage compiles and
   packages `app.jar`, then only the JRE-alpine runtime stage + that jar become the final image — the
   Gradle distribution, JDK, and source tree never end up in what actually runs).
3. Start a container from that image with your configured environment variables injected.
4. Poll the health check path until it responds successfully, then route traffic to the new instance and
   retire the old one (zero-downtime deploy).

**Deploying from GitHub going forward:** by default, Render **auto-deploys on every push** to the
connected branch — merge/push to `main` (or whichever branch you selected) and Render picks it up within
seconds, no manual step needed. You can disable auto-deploy per-service in Settings if you'd rather trigger
deploys manually via **Manual Deploy**.

---

## 6. Verify

```bash
curl https://<your-service>.onrender.com/api/health
# {"status":"UP"}

curl -i -X POST https://<your-service>.onrender.com/api/submissions \
  -H "Content-Type: application/json" \
  -d '{"fullName": "Test User", "email": "test@example.com", "message": "Deployment check"}'
# 201 Created, body is the saved submission with status "NEW"
```

If both succeed, the full path (Render → Spring Boot → Neon) is confirmed working end to end.

---

## 7. Inspecting logs

From the service page, the **Logs** tab shows:
- **Live tail** — streams stdout/stderr as the running container produces it (this is where Spring Boot's
  startup banner, Flyway migration output, Hibernate `validate` result, and the admin-seeder's "Seeded
  initial admin user" line all show up on first boot).
- **Historical logs** — scrollable/searchable past output, including logs from previous deploys/restarts,
  useful for diagnosing a crash that already happened.
- Log level in production is controlled by `application-prod.yml` (SQL logging off) plus Spring Boot's
  default `INFO` root level — verbose enough to see request-level errors and startup diagnostics without
  drowning in per-query SQL noise.

---

## Troubleshooting

**Database connection failure** (logs show `Connection refused`, `FATAL: password authentication failed`,
or the app hangs on startup trying to reach Postgres):
- Confirm `DB_URL` is the **JDBC** form (`jdbc:postgresql://...`), not Neon's raw `postgresql://` string.
- Confirm `?sslmode=require` is present in `DB_URL` — Neon rejects non-SSL connections.
- Confirm `DB_USERNAME`/`DB_PASSWORD` match exactly what Neon shows (Neon connection strings are
  case-sensitive and sometimes include a project-specific suffix on the username — copy it exactly, don't
  retype it).
- Neon's free tier auto-suspends an inactive database and wakes on the next connection attempt, which can
  take a few seconds — a slow-but-eventually-successful first connection after idle time is expected, not
  an error, but a connection that never succeeds is a real credentials/URL problem.

**Port binding failure** (Render reports the service never became healthy / "no open ports detected"):
- Confirm the `server.port: ${PORT:8080}` change (see step 0) is actually deployed — check the running
  commit matches what you expect.
- Render injects `PORT` automatically for Docker services; you don't need to set it yourself as an
  environment variable — doing so isn't wrong, but it's redundant since Render already provides it.
- Check the Logs tab for the Spring Boot startup line (`Tomcat started on port(s): ...`) to see what port
  the app actually bound to, and compare against what Render expected.

**CORS errors** (browser console shows a blocked cross-origin request from the frontend):
- `ALLOWED_ORIGINS` must be an **exact match** for the frontend's origin: same scheme (`https://`), same
  host, no trailing slash, no path. `https://myapp.vercel.app` and `https://myapp.vercel.app/` are
  different strings to this check.
- Remember env var changes need a redeploy/restart to take effect (Render restarts the container
  automatically when you save an environment variable change, but it's not instantaneous — wait for the
  new instance to show healthy before retesting).
- Multiple origins can be comma-separated (see `CorsConfig`'s handling of `app.cors.allowed-origins`).

**Application startup failure** (deploy shows as failed, container exits immediately):
- Check the Logs tab for a stack trace — the two most common causes here are a Flyway migration mismatch
  (e.g. the database already has tables that don't match `V1__init_schema.sql` and no
  `flyway_schema_history` row — `spring.flyway.baseline-on-migrate: true` is already set to handle a
  pre-existing schema gracefully, but a genuinely incompatible schema will still fail loudly) or a missing/
  malformed required environment variable.
- Render's free-tier instances have limited memory (512 MB); if logs show an `OutOfMemoryError` or the
  process gets silently killed, consider a paid instance tier or tuning JVM heap flags via a `JAVA_TOOL_OPTIONS`
  environment variable (e.g. `-Xmx256m`) — not needed for this app's current size under normal load, but
  worth knowing if it comes up later.

---

## What's still open

- Actually creating the Render/Neon accounts, connecting them, and clicking through this guide — none of
  that has been done from this environment (no credentials, and account creation is squarely a
  you-must-do-this-yourself step regardless).
- `ALLOWED_ORIGINS` will need updating once Phase 24 (Vercel) gives you a real frontend URL.
- Phase 25 (Production Security Review) hasn't happened yet — treat this deployment as functional but not
  yet security-reviewed for production traffic.
