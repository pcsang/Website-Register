# Project Goal

Build a simple Information Collection & Admin Management System.

## Main features

### Public User

- Access public form
- Enter personal/information data
- Validate input
- Submit data
- Show success/error message

### Admin

- Login
- View dashboard
- View submission statistics
- View submissions
- Search
- Filter by status
- Pagination
- View submission details
- Update submission status

---

# Technology Stack

```text
Frontend
├── Angular
├── Angular Material
├── Reactive Forms
└── HttpClient

Backend
├── Java 21
├── Spring Boot 3.x
├── Spring Web
├── Spring Data JPA
├── Hibernate
├── Jakarta Validation
├── Spring Security
└── Maven

Database
└── PostgreSQL

Deployment
├── GitHub
├── Vercel → Angular
├── Render → Spring Boot
└── Neon → PostgreSQL
```

---

# Target Architecture

```text
                    Internet
                       │
                       ▼
                ┌─────────────┐
                │   Angular   │
                │   Vercel    │
                └──────┬──────┘
                       │
                    HTTPS
                       │
                       ▼
              ┌─────────────────┐
              │   Spring Boot   │
              │     Render      │
              └────────┬────────┘
                       │
                  Spring Data JPA
                       │
                       ▼
                ┌─────────────┐
                │ PostgreSQL  │
                │    Neon     │
                └─────────────┘
```

---

# Backend Package Structure

```text
src/main/java/com/example/information/

├── config/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── entity/
├── enums/
├── exception/
├── mapper/
├── repository/
├── security/
├── service/
└── InformationApplication.java
```

---

# API Design

## Public

```text
POST /api/submissions
GET  /api/health
```

## Admin

```text
POST  /api/auth/login

GET   /api/admin/dashboard/summary

GET   /api/admin/submissions
GET   /api/admin/submissions/{id}

PATCH /api/admin/submissions/{id}/status
```

---

# PHASE 1 — Spring Boot Project Setup

## Step 1 — Initialize Spring Boot

### Goal

Create the initial backend.

### Prompt

```text
We are building a simple Information Collection and Admin Management System.

Implement Step 1 only: initialize the Spring Boot backend.

Technology stack:

- Java 21
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Data JPA
- Jakarta Validation
- PostgreSQL
- Spring Boot Actuator if appropriate

Frontend will be Angular, but DO NOT implement frontend yet.

Use this package structure:

config
controller
dto/request
dto/response
entity
enums
exception
mapper
repository
security
service

Requirements:

1. Inspect the existing repository first.
2. If a Spring Boot project already exists, reuse it instead of creating another project.
3. Configure Java 21.
4. Configure required Maven dependencies.
5. Configure PostgreSQL dependencies.
6. Create application.yml.
7. Create a simple health endpoint:

GET /api/health

Response:

{
  "status": "UP"
}

8. Use constructor dependency injection.
9. Do not implement authentication yet.
10. Do not implement Submission features yet.
11. Keep the architecture simple.
12. Do not introduce unnecessary libraries.

After implementation:

- List all files created or modified.
- Explain the project structure.
- Show how to start the application.
- Show how to test GET /api/health.
- Run Maven build/tests and report the result.
```

---

# PHASE 2 — PostgreSQL Configuration

## Step 2 — Connect Spring Boot to PostgreSQL

### Prompt

```text
We are implementing Step 2 only.

Configure PostgreSQL for the existing Spring Boot application.

Technology:

- Java 21
- Spring Boot 3
- Spring Data JPA
- Hibernate
- PostgreSQL

Requirements:

1. Inspect the existing application.yml and pom.xml first.
2. Configure PostgreSQL datasource.
3. Configure Spring Data JPA.
4. Use environment variables for database credentials.

Expected variables:

DB_URL
DB_USERNAME
DB_PASSWORD

5. Do not hardcode production credentials.
6. Configure Hibernate appropriately for local development.
7. Enable useful SQL logging only for development if appropriate.
8. Explain the difference between:

ddl-auto=create
ddl-auto=update
ddl-auto=validate
ddl-auto=none

9. Recommend the appropriate option for this project.
10. Do not create business entities yet.

Example local database:

database: information_db
username: postgres

After implementation:

- Show the application.yml.
- Explain each important configuration.
- Show how to create the PostgreSQL database.
- Start/build the application and verify that it can connect to PostgreSQL.
```

---

# PHASE 3 — Submission Domain

## Step 3 — Create Submission Entity

### Model

```text
Submission

id
fullName
email
phone
company
position
message
status
createdAt
updatedAt
```

### Status

```text
NEW
IN_PROGRESS
COMPLETED
```

### Prompt

```text
We are implementing Step 3 only.

Create the Submission domain model.

Fields:

id: Long
fullName: String
email: String
phone: String
company: String
position: String
message: String
status: SubmissionStatus
createdAt: LocalDateTime
updatedAt: LocalDateTime

Create enum:

SubmissionStatus

Values:

NEW
IN_PROGRESS
COMPLETED

Requirements:

1. Inspect the existing project structure first.
2. Create Submission as a JPA entity.
3. Create SubmissionStatus enum.
4. Use @Enumerated(EnumType.STRING).
5. Use appropriate PostgreSQL column types.
6. Define appropriate VARCHAR lengths.
7. FullName cannot be null.
8. Email cannot be null.
9. Status cannot be null.
10. ID should be generated automatically.
11. Automatically manage createdAt and updatedAt.
12. Choose between:
    - @PrePersist / @PreUpdate
    - Spring Data JPA auditing

For this small project, choose the simpler appropriate solution and explain why.

13. Create SubmissionRepository extending JpaRepository.
14. Do not create Controller yet.
15. Do not create API endpoints yet.

After implementation:

- List created/modified files.
- Explain the entity mapping.
- Show the expected PostgreSQL table structure.
- Build the application and report any errors.
```

---

# PHASE 4 — Public Submission API

## Step 4 — POST Submission

```text
POST /api/submissions
```

### Request

```json
{
  "fullName": "Nguyen Van A",
  "email": "a@gmail.com",
  "phone": "0901234567",
  "company": "ABC Company",
  "position": "Software Developer",
  "message": "Hello"
}
```

### Prompt

```text
We are implementing Step 4 only.

Implement the public Submission API.

Endpoint:

POST /api/submissions

Example request:

{
  "fullName": "Nguyen Van A",
  "email": "a@gmail.com",
  "phone": "0901234567",
  "company": "ABC Company",
  "position": "Software Developer",
  "message": "Hello"
}

Requirements:

1. Inspect the existing Submission entity and Repository first.

2. Create:

CreateSubmissionRequest
SubmissionResponse
SubmissionMapper
SubmissionService
SubmissionController

3. Use Jakarta Validation.

Validation:

fullName:
- required
- max 200 characters

email:
- required
- valid email
- max 255 characters

phone:
- optional
- max 30 characters

company:
- optional
- max 200 characters

position:
- optional
- max 200 characters

message:
- optional
- max 2000 characters

4. Automatically set:

status = NEW

5. Save using SubmissionRepository.

6. Return HTTP:

201 Created

7. Do not expose the JPA Entity directly from Controller.

8. Follow:

Controller
    ↓
Service
    ↓
Repository

9. Use constructor injection.

10. Use @Transactional appropriately.

11. Do not implement Admin APIs yet.

After implementation:

- List modified/created files.
- Explain the request flow.
- Show example request/response.
- Show how to test using curl or Postman.
- Run tests/build.
```

---

# PHASE 5 — Global Exception Handling

## Step 5 — Handle Errors

### Prompt

```text
We are implementing centralized exception handling.

Inspect the current Spring Boot application first.

Implement global API exception handling using:

@RestControllerAdvice
@ExceptionHandler

Requirements:

1. Handle Jakarta Validation errors.

Example response:

{
  "status": 400,
  "message": "Validation failed",
  "errors": {
    "email": "must be a valid email",
    "fullName": "must not be blank"
  },
  "timestamp": "...",
  "path": "/api/submissions"
}

2. Handle ResourceNotFoundException.
3. Handle unexpected exceptions.
4. Return a consistent error response.
5. Never expose stack traces to API clients.
6. Log unexpected exceptions.
7. Include useful fields: status, message, timestamp, path.
8. Include field errors for validation failures.
9. Use appropriate HTTP status codes.
10. Do not refactor unrelated business code.

After implementation:

- Explain exception flow.
- List created files.
- Show example responses for 400, 404 and 500.
- Run tests/build.
```

---

# PHASE 6 — Admin Submission List

## Step 6 — Search + Filter + Pagination

```text
GET /api/admin/submissions
```

### Prompt

```text
We are implementing the Admin Submission List API.

Endpoint:

GET /api/admin/submissions

Query parameters:

page
size
search
status
sort

Example:

GET /api/admin/submissions?page=0&size=20&search=nguyen&status=NEW

Requirements:

1. Inspect the existing Repository and Service.
2. Implement server-side pagination using Spring Data Pageable.
3. Search across: fullName, email, phone, company.
4. Filter by SubmissionStatus.
5. Default sorting: createdAt DESC.
6. Return DTOs, not JPA entities.
7. Response should contain: content, page, size, totalElements, totalPages.
8. Use a custom PageResponse<T> if it makes the API cleaner.
9. Prevent invalid page size values.
10. Avoid loading all records into memory.
11. Execute filtering at database level.
12. Explain whether to use derived query methods, JPQL, or Specification. Choose the simplest maintainable approach for search + optional filters.
13. Do not implement authentication yet.

After implementation:

- Show example request.
- Show response JSON.
- Explain generated database query behavior.
- Run tests/build.
```

---

# PHASE 7 — Submission Detail

## Step 7

```text
GET /api/admin/submissions/{id}
```

### Prompt

```text
We are implementing the Admin Submission Detail API.

Endpoint:

GET /api/admin/submissions/{id}

Requirements:

1. Inspect existing SubmissionService and Repository.
2. Find Submission by ID.
3. Return SubmissionResponse.
4. Never expose JPA Entity directly.
5. If ID does not exist, throw ResourceNotFoundException.
6. GlobalExceptionHandler should convert it to HTTP 404.
7. Do not catch generic Exception inside Controller.
8. Keep Controller thin.
9. Add unit tests for:
   - submission exists
   - submission does not exist

After implementation:

- List modified files.
- Explain request flow.
- Show 200 response.
- Show 404 response.
- Run Maven tests.
```

---

# PHASE 8 — Update Status

## Step 8

```text
PATCH /api/admin/submissions/{id}/status
```

### Prompt

```text
Implement Admin Submission Status Update.

Endpoint:

PATCH /api/admin/submissions/{id}/status

Request:

{
  "status": "IN_PROGRESS"
}

Allowed statuses:

NEW
IN_PROGRESS
COMPLETED

Requirements:

1. Create UpdateSubmissionStatusRequest.
2. Validate status using SubmissionStatus enum.
3. Find Submission by ID.
4. Return HTTP 404 if not found.
5. Update status.
6. Update updatedAt automatically.
7. Save changes transactionally.
8. Return updated SubmissionResponse.
9. Invalid status should return HTTP 400.
10. Keep Controller thin.
11. Business logic belongs in Service.
12. Add tests for successful update, submission not found, and invalid status.

After implementation:

- Show request/response examples.
- Explain how enum JSON deserialization works.
- Run tests/build.
```

---

# PHASE 9 — Dashboard Summary

## Step 9

```text
GET /api/admin/dashboard/summary
```

### Prompt

```text
Implement the Admin Dashboard Summary API.

Endpoint:

GET /api/admin/dashboard/summary

Response:

{
  "total": 150,
  "new": 30,
  "inProgress": 40,
  "completed": 80,
  "submittedToday": 12
}

Requirements:

1. Create DashboardSummaryResponse.
2. Calculate total, NEW, IN_PROGRESS, COMPLETED, and submissions created today.
3. Query PostgreSQL efficiently.
4. Do not call findAll() and count records in Java memory.
5. Use database COUNT queries.
6. Consider timezone when calculating "today".
7. Keep Controller thin.
8. Create DashboardService if appropriate.
9. Explain whether multiple count queries are acceptable for this small application.
10. Avoid premature optimization.

After implementation:

- List changed files.
- Show endpoint response.
- Explain database queries.
- Run tests/build.
```

---

# PHASE 10 — CORS

## Step 10

### Prompt

```text
Configure CORS for the Spring Boot backend.

Frontend development URL:

http://localhost:4200

Backend:

http://localhost:8080

Requirements:

1. Allow Angular development frontend to call the API.
2. Configure CORS globally.
3. Allow GET, POST, PATCH, OPTIONS.
4. Allow required headers.
5. Do not use "*" blindly for production.
6. Read allowed frontend origins from configuration.

Example environment variable:

ALLOWED_ORIGINS=http://localhost:4200

7. Design configuration so production can later allow:
https://my-app.vercel.app

8. Explain CORS and why browsers enforce it.
9. Do not implement Spring Security yet unless required.

After implementation:

- Show configuration.
- Explain local vs production setup.
- Run build.
```

---

# PHASE 11 — Angular Setup

## Step 11

### Prompt

```text
We are now starting the frontend.

Backend already exists using Spring Boot.

Create an Angular frontend.

Technology:

- Angular
- Angular Material
- Reactive Forms
- HttpClient

Pages:

/form
/admin/dashboard
/admin/submissions/:id

Use this structure:

src/app/

core/
  services/
  interceptors/
  guards/

models/

public/
  information-form/

admin/
  dashboard/
  submission-detail/

shared/

Requirements:

1. Inspect existing frontend if one already exists.
2. Use modern Angular conventions.
3. Use standalone components if appropriate.
4. Install/configure Angular Material.
5. Configure HttpClient.
6. Configure routing.
7. Create placeholder pages.
8. Configure backend API base URL using environment configuration.
9. Do not implement authentication yet.
10. Do not implement full UI yet.

Backend development URL:

http://localhost:8080

After implementation:

- Explain structure.
- List created files.
- Show routes.
- Run Angular build.
```

---

# PHASE 12 — Angular API Models + Service

## Step 12

### Prompt

```text
Implement the Angular API layer for Submission features.

Backend APIs:

POST /api/submissions
GET /api/admin/submissions
GET /api/admin/submissions/{id}
PATCH /api/admin/submissions/{id}/status
GET /api/admin/dashboard/summary

Create TypeScript interfaces:

CreateSubmissionRequest
Submission
SubmissionStatus
PageResponse<T>
DashboardSummary
UpdateSubmissionStatusRequest

Create SubmissionService.

Requirements:

1. Use Angular HttpClient.
2. Use strongly typed Observables.
3. Do not use any.
4. Keep API URL centralized.
5. Support pagination.
6. Support search.
7. Support status filter.
8. Properly encode HttpParams.
9. Keep API calls out of Components where possible.
10. Do not add unnecessary state management libraries.

After implementation:

- List files.
- Explain each method.
- Run Angular build.
```

---

# PHASE 13 — Public User Form

## Step 13

### Prompt

```text
Build the public Information Form.

Route:

/form

Use:

Angular Material
Reactive Forms

Fields:

Full Name *
Email *
Phone
Company
Position
Message

Requirements:

1. Use FormBuilder.
2. Validation must match backend:
   Full Name: required, max 200
   Email: required, valid email, max 255
   Phone: max 30
   Company: max 200
   Position: max 200
   Message: max 2000
3. Display validation messages.
4. Submit using SubmissionService.createSubmission().
5. Disable Submit while API request is running.
6. Prevent double submission.
7. Show success notification.
8. Reset form after successful submission.
9. Show API error.
10. Make UI responsive.
11. Do not put HTTP logic directly inside the template.
12. Use Angular Material components.

After implementation:

- Provide component TypeScript.
- HTML.
- SCSS.
- Explain form flow.
- Run Angular build.
```

---

# PHASE 14 — Admin Dashboard UI

## Step 14

### Prompt

```text
Build the Angular Admin Dashboard.

Route:

/admin/dashboard

Backend APIs:

GET /api/admin/dashboard/summary
GET /api/admin/submissions

Dashboard cards:

Total
New
In Progress
Completed
Submitted Today

Submission table columns:

Full Name
Email
Phone
Company
Status
Created At
Action

Requirements:

1. Use Angular Material Card.
2. Use MatTable.
3. Use MatPaginator.
4. Use backend/server-side pagination.
5. Add search input.
6. Add status filter: ALL, NEW, IN_PROGRESS, COMPLETED.
7. Default sorting should match backend createdAt DESC.
8. View button navigates to /admin/submissions/{id}.
9. Show loading indicator.
10. Show empty state.
11. Handle API errors.
12. Avoid calling API for every keystroke. Use debounceTime and distinctUntilChanged if appropriate.
13. Make dashboard responsive.
14. Keep API logic in SubmissionService.

After implementation:

- Provide TypeScript.
- HTML.
- SCSS.
- Explain data flow.
- Run Angular build.
```

---

# PHASE 15 — Submission Detail UI

## Step 15

### Prompt

```text
Build the Angular Admin Submission Detail page.

Route:

/admin/submissions/:id

Backend APIs:

GET /api/admin/submissions/{id}
PATCH /api/admin/submissions/{id}/status

Display:

Full Name
Email
Phone
Company
Position
Message
Status
Created At
Updated At

Requirements:

1. Read ID from ActivatedRoute.
2. Load Submission.
3. Show loading state.
4. Handle HTTP 404.
5. Add Angular Material status dropdown with NEW, IN_PROGRESS, COMPLETED.
6. Allow Admin to update status.
7. Disable update button while saving.
8. Show success notification.
9. Show error notification.
10. Add Back to Dashboard.
11. Keep HTTP logic in SubmissionService.
12. Make UI responsive.

After implementation:

- Provide TS.
- HTML.
- SCSS.
- Explain flow.
- Run build.
```

---

# PHASE 16 — Spring Security

## Step 16 — Admin Authentication

### Prompt

```text
We are now implementing Admin authentication.

Current stack:

Java 21
Spring Boot 3
Spring Security
Angular
PostgreSQL

Public endpoints:

GET /api/health
POST /api/submissions

Admin endpoints:

GET /api/admin/**
PATCH /api/admin/**

Requirements:

1. Inspect current backend before implementation.
2. Design simple authentication appropriate for a small application.
3. Use Spring Security 6.
4. Use JWT authentication.
5. Create AdminUser entity if necessary with id, username, passwordHash, role, createdAt.
6. Never store plaintext passwords.
7. Use BCryptPasswordEncoder.
8. Create POST /api/auth/login.
9. Login request:
{
  "username": "...",
  "password": "..."
}
10. Return JWT.
11. Protect /api/admin/**.
12. Allow POST /api/submissions without authentication.
13. Configure stateless authentication.
14. Implement JWT filter.
15. Configure SecurityFilterChain.
16. Use ROLE_ADMIN.
17. Store JWT secret in environment variable.
18. Add expiration.
19. Do not hardcode production secrets.
20. Explain authentication flow before implementing.

After implementation:

- List security files.
- Explain login flow.
- Explain request authentication flow.
- Show curl examples.
- Add security tests.
- Run Maven tests.
```

---

# PHASE 17 — Angular Authentication

## Step 17

### Prompt

```text
Implement Angular Admin authentication.

Backend:

POST /api/auth/login

Protected backend APIs:

/api/admin/**

Frontend routes:

/form → public
/admin/login → public
/admin/dashboard → protected
/admin/submissions/:id → protected

Requirements:

1. Create Admin Login page.
2. Create AuthService.
3. Create LoginRequest/LoginResponse interfaces.
4. Store authentication state.
5. Create HTTP interceptor.
6. Send Authorization: Bearer <token> for Admin API calls.
7. Create route guard.
8. Redirect unauthenticated users to /admin/login.
9. Handle HTTP 401.
10. Implement logout.
11. Do not send JWT to public endpoints unnecessarily.
12. Explain security limitations of storing JWT in browser storage.
13. For this small project, choose an appropriate storage strategy and explain the tradeoff.

After implementation:

- List files.
- Explain login flow.
- Explain guard.
- Explain interceptor.
- Run Angular build.
```

---

# PHASE 18 — Backend Unit Tests

## Step 18

### Prompt

```text
Add backend unit tests.

Technology:

JUnit 5
Mockito

Focus on Service layer.

Create tests for SubmissionService:

1. Create Submission successfully.
2. New Submission automatically receives NEW.
3. Get Submission successfully.
4. Submission not found.
5. Update status successfully.
6. Pagination/search behavior where appropriate.

Create tests for DashboardService:

7. Dashboard summary returns expected counts.

Requirements:

- Use JUnit 5.
- Use Mockito.
- Follow Arrange / Act / Assert.
- Do not start the full Spring context for unit tests.
- Mock Repository dependencies.
- Keep tests readable.
- Avoid testing framework implementation details.

After implementation:

Run:

mvn test

Report:

tests passed
tests failed
coverage gaps
```

---

# PHASE 19 — Integration Tests

## Step 19

### Prompt

```text
Add Spring Boot integration tests for the Submission APIs.

Test:

POST /api/submissions
GET /api/admin/submissions
GET /api/admin/submissions/{id}
PATCH /api/admin/submissions/{id}/status
GET /api/admin/dashboard/summary

Requirements:

1. Use @SpringBootTest or @WebMvcTest appropriately.
2. Use MockMvc.
3. Test JSON request/response.
4. Test HTTP 200, 201, 400, 404.
5. Test validation.
6. Test invalid status.
7. Test pagination.
8. Test search/filter.
9. If database integration is required, recommend whether to use H2 or PostgreSQL Testcontainers.
10. Because production uses PostgreSQL, explain the tradeoff.
11. Do not make tests depend on production database.

Run complete test suite after implementation.
```

---

# PHASE 20 — Dockerize Spring Boot

## Step 20

### Prompt

```text
Dockerize the Spring Boot backend.

Technology:

Java 21
Spring Boot 3
Maven

Requirements:

1. Create Dockerfile.
2. Use multi-stage Docker build.
3. Build stage: Maven + Java 21.
4. Runtime: Java 21 JRE.
5. Do not run application as root if practical.
6. Expose application port.
7. Use environment variables:
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
ALLOWED_ORIGINS
8. Add .dockerignore.
9. Keep image reasonably small.
10. Do not put credentials inside Dockerfile.
11. Show docker build and docker run commands.
12. Explain Docker layers.
13. Build the Docker image and verify it starts successfully if Docker is available.
```

---

# PHASE 21 — Docker Compose for Local Development

## Step 21

### Prompt

```text
Create Docker Compose configuration for local development.

Services:

backend
postgres

PostgreSQL:

database: information_db

Requirements:

1. Create docker-compose.yml.
2. Configure PostgreSQL container.
3. Configure Spring Boot backend container.
4. Backend should connect to PostgreSQL using service name postgres, not localhost.
5. Use environment variables.
6. Create persistent PostgreSQL volume.
7. Add healthcheck for PostgreSQL.
8. Configure backend dependency appropriately.
9. Do not hardcode sensitive production credentials.
10. Explain Docker networking.

Commands:

docker compose build
docker compose up -d
docker compose logs -f
docker compose down
docker compose down -v

Explain the difference between down and down -v.
```

---

# PHASE 22 — Production Database: Neon

## Step 22

### Prompt

```text
Prepare the Spring Boot application for deployment using Neon PostgreSQL.

Production architecture:

Angular → Vercel
Spring Boot → Render
PostgreSQL → Neon

Requirements:

1. Do not hardcode Neon credentials.
2. Spring Boot must read DB_URL, DB_USERNAME, DB_PASSWORD.
3. Configure PostgreSQL SSL if Neon requires it.
4. Review Hibernate/JPA production settings.
5. Disable unnecessary SQL logging.
6. Recommend production ddl-auto setting.
7. Explain database migration strategy.
8. For this small project, recommend whether we should introduce Flyway.
9. If yes:
   - configure Flyway
   - create initial migration
   - stop relying on Hibernate schema auto-update
10. Explain how local and production DB configurations differ.

Do not deploy yet.

Only prepare database configuration and migration strategy.
```

---

# PHASE 23 — Deploy Spring Boot to Render

## Step 23

### Prompt

```text
Prepare and deploy the Spring Boot backend to Render.

Architecture:

GitHub
   ↓
Render
   ↓
Spring Boot
   ↓
Neon PostgreSQL

Requirements:

1. Inspect the current Dockerfile.
2. Make it production-ready for Render.
3. Ensure application listens on the port provided by Render using PORT if required.
4. Configure DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, ALLOWED_ORIGINS.
5. Do not commit secrets.
6. Configure Neon PostgreSQL.
7. Configure production profile.
8. Configure logging appropriately.
9. Add health endpoint.
10. Explain Render build/start configuration.
11. Explain how to deploy from GitHub.
12. Explain how to inspect Render logs.
13. Explain how to troubleshoot database connection failure, port binding failure, CORS, and application startup failure.

Expected production API:

https://<backend>.onrender.com

After deployment, verify:

GET /api/health
POST /api/submissions
```

---

# PHASE 24 — Deploy Angular to Vercel

## Step 24

### Prompt

```text
Prepare the Angular application for Vercel deployment.

Production backend:

https://<backend>.onrender.com

Requirements:

1. Configure production API URL.
2. Do not hardcode localhost.
3. Build Angular production bundle.
4. Configure Vercel.
5. Ensure Angular client-side routing works when refreshing:
   /form
   /admin/login
   /admin/dashboard
   /admin/submissions/{id}
6. Configure SPA fallback/rewrite if required.
7. Connect Vercel to GitHub.
8. Configure environment variables if appropriate.
9. Deploy.
10. Verify frontend can call Render backend.
11. Identify and fix possible CORS issues.
12. Verify Public form submission, Admin login, Admin dashboard, Submission detail, and Status update.
```

---

# PHASE 25 — Production Security Review

## Step 25

### Prompt

```text
Perform a security review of the application before production deployment.

Stack:

Angular
Spring Boot
Spring Security
JWT
PostgreSQL
Vercel
Render
Neon

Review:

1. Authentication.
2. Authorization.
3. Password hashing.
4. JWT secret management.
5. JWT expiration.
6. CORS.
7. Input validation.
8. SQL injection.
9. XSS.
10. Sensitive data exposure.
11. Error responses.
12. Logging.
13. HTTPS.
14. Database credentials.
15. Environment variables.
16. Admin endpoints.
17. Rate limiting for public submission endpoint.
18. Spam/bot submissions.

Classify findings:

CRITICAL
HIGH
MEDIUM
LOW
NICE TO HAVE

Do not over-engineer the application.

Focus on realistic security risks for a small public web application.
```

---

# PHASE 26 — Final Architecture Review

## Step 26

### Prompt

```text
Perform a final architecture and code review of the entire application.

Architecture:

Angular
    ↓
REST API
    ↓
Spring Boot
    ↓
Controller
    ↓
Service
    ↓
Repository
    ↓
Spring Data JPA
    ↓
PostgreSQL

Deployment:

Angular → Vercel
Spring Boot → Render
PostgreSQL → Neon

Review:

1. Project structure.
2. REST API design.
3. DTO design.
4. Entity design.
5. Validation.
6. Exception handling.
7. Search/filter/pagination.
8. Database performance.
9. Spring Security.
10. JWT implementation.
11. Angular architecture.
12. Angular API services.
13. Error handling.
14. Docker.
15. Production configuration.
16. CORS.
17. Security.
18. Logging.
19. Testing.
20. Deployment.

Classify recommendations:

Critical
Should Improve
Nice to Have

Important:

This is a small application.

Do not recommend unnecessary:
microservices
Kafka
Kubernetes
CQRS
event sourcing
Redis
complex domain-driven design

unless there is a concrete reason.

Prefer simplicity, maintainability and low operating cost.
```

---

# MASTER PROMPT FOR EVERY CODING STEP

```text
IMPORTANT WORKING RULES

Before changing code:

1. Inspect the existing project.
2. Understand the current architecture.
3. Identify the files related to this task.
4. Briefly explain the implementation plan.

During implementation:

5. Implement ONLY the current task.
6. Do not implement future roadmap features.
7. Do not refactor unrelated code.
8. Follow existing naming conventions.
9. Follow existing architecture.
10. Do not introduce unnecessary dependencies.
11. Prefer simple solutions.
12. Use constructor dependency injection.
13. Do not expose JPA entities through REST APIs.
14. Keep Controllers thin.
15. Put business logic in Services.
16. Put database access in Repositories.
17. Use DTOs for API requests/responses.
18. Use transactions where appropriate.
19. Never hardcode passwords, secrets or production credentials.

After implementation:

20. List all files created.
21. List all files modified.
22. Explain important implementation decisions.
23. Build the project.
24. Run relevant tests.
25. Fix compilation/test failures caused by the implementation.
26. Show how I can manually test the feature.
27. Do not continue to the next roadmap step automatically.
```

---

# Recommended Execution Order

```text
STEP 01 Spring Boot Setup
STEP 02 PostgreSQL Connection
STEP 03 Submission Entity
STEP 04 POST /api/submissions
STEP 05 Exception Handling
STEP 06 Admin List API
STEP 07 Admin Detail API
STEP 08 Update Status
STEP 09 Dashboard Summary
STEP 10 CORS
────────── BACKEND MVP ──────────
STEP 11 Angular Setup
STEP 12 Angular API Service
STEP 13 User Form
────────── USER MVP ─────────────
STEP 14 Admin Dashboard
STEP 15 Admin Detail
────────── ADMIN MVP ────────────
STEP 16 Spring Security + JWT
STEP 17 Angular Authentication
────────── SECURED MVP ──────────
STEP 18 Unit Tests
STEP 19 Integration Tests
STEP 20 Backend Docker
STEP 21 Local Docker Compose
STEP 22 Neon PostgreSQL
STEP 23 Render Backend
STEP 24 Vercel Frontend
STEP 25 Security Review
STEP 26 Final Review
```

# MVP Milestones

## Milestone 1 — Backend MVP

```text
POST /api/submissions
        ↓
Spring Boot
        ↓
PostgreSQL
```

At this point, test everything with Postman before starting Angular.

## Milestone 2 — Public MVP

```text
Angular Form
      ↓
Spring Boot
      ↓
PostgreSQL
```

A real user can submit information.

## Milestone 3 — Admin MVP

```text
              PostgreSQL
                   ↑
                   │
              Spring Boot
                   ↑
             REST API
             ↗          ↖
Angular Form          Admin Dashboard
```

Admin can see the submitted data.

## Milestone 4 — Secured MVP

```text
Public
/form
   ↓
POST /api/submissions
   ↓
Spring Boot
   ↓
PostgreSQL

Admin
/admin/login
   ↓
JWT
   ↓
/admin/dashboard
   ↓
/api/admin/**
```

## Milestone 5 — Internet Deployment

```text
                    INTERNET
                       │
             ┌─────────┴─────────┐
             │                   │
          Public               Admin
             │                   │
             └─────────┬─────────┘
                       ▼
                 Angular App
                    Vercel
                       │
                     HTTPS
                       ▼
                 Spring Boot
                    Render
                       │
                       ▼
                  PostgreSQL
                     Neon
```

At this point, the application is publicly accessible on the Internet.

---

# Important Development Rule

Do not ask the coding agent:

"Build the whole application."

Instead, always work one step at a time:

```text
We are currently implementing Step 4 only.

Do not implement Step 5 or future features.

First inspect the existing codebase.

Then explain your implementation plan.

Then implement the feature.

After implementation:
- build the project
- run tests
- list changed files
- explain how to test it

Stop after Step 4 is complete.
```

This approach makes AI-generated code significantly easier to review, debug and maintain.
