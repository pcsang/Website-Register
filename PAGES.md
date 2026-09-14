# Pages

Describes every page currently implemented in the Angular app (`clientUI/`), what it's for, what's on
it, and how it behaves. Routes are defined in `clientUI/src/app/app.routes.ts`.

> **Auth status:** Spring Security / Angular auth (roadmap Phases 16–17) are not implemented yet. The
> `/admin/*` routes below are reachable by anyone who knows the URL — there is no login page and no route
> guard. Treat the "Admin" label below as an intended-audience distinction, not an enforced one.

| Route | Component | Audience |
|---|---|---|
| `/` | redirects to `/form` | — |
| `/form` | `InformationFormComponent` | Public |
| `/admin/dashboard` | `DashboardComponent` | Admin |
| `/admin/submissions/:id` | `SubmissionDetailComponent` | Admin |

---

## Public

### Information Form — `/form`

**File:** `clientUI/src/app/public/information-form/`

The public-facing entry point of the site. An anonymous visitor fills in a contact/inquiry form; on
submit it calls `POST /api/submissions` and the backend creates a new `Submission` with status `NEW`.

**Fields:**

| Field | Required | Validation |
|---|---|---|
| Full Name | Yes | max 200 chars |
| Email | No | must be a valid email format if present; max 255 chars |
| Phone | No | max 30 chars |
| Message | No | multi-line textarea; max 2000 chars |

Validators mirror the backend's `CreateSubmissionRequest` Jakarta Validation constraints exactly, so a
form that passes client-side validation should not fail server-side validation.

**Behavior:**
- Submit button shows a spinner and disables while the request is in flight (prevents double submit).
- On success: form resets to empty and a "Submission sent successfully." snackbar appears.
- On failure: a snackbar shows the backend's error message (falls back to a generic message if the
  response doesn't have the expected shape).
- No navigation away from the page — a visitor can submit multiple times in a row.

---

## Admin

### Admin Dashboard — `/admin/dashboard`

**File:** `clientUI/src/app/admin/dashboard/`

Landing page for the admin side: a summary of submission counts plus a searchable, filterable,
paginated table of all submissions, each linking through to its detail page.

**Summary cards** (from `GET /api/submissions/summary`): Total, New, In Progress, Completed, Submitted
Today. Shown as a row of cards with a spinner while loading; failures show a snackbar without blocking
the table below.

**Submissions table** (from `GET /api/submissions`, server-side paginated):

- Columns: Full Name, Email, Phone, Status, Created At, Action (a "View" button).
- **Search box:** free-text, matched against name/email/phone server-side; debounced 300ms before
  triggering a request, and resets to page 1 on change.
- **Status filter:** dropdown of `ALL` / `NEW` / `IN_PROGRESS` / `COMPLETED`; resets to page 1 on change.
- **Paginator:** page sizes 10/20/50 (default 20), first/last buttons enabled.
- Empty state ("No submissions found.") when a search/filter returns nothing.
- Row action navigates to `/admin/submissions/:id` for that row.

### Submission Detail — `/admin/submissions/:id`

**File:** `clientUI/src/app/admin/submission-detail/`

Detail view for a single submission (`GET /api/submissions/:id`), with the ability to change its status
(`PATCH` via `updateStatus`).

**Displayed fields:** Full Name, Email, Phone, Message, Status, Created At, Updated At (each blank
optional field shows as `—`).

**Status update:** a dropdown pre-filled with the submission's current status, offering `NEW` /
`IN_PROGRESS` / `COMPLETED`, plus an "Update Status" button that:
- is disabled while a save is in flight, while the submission hasn't loaded yet, or when the selected
  value equals the already-saved status (no-op guard);
- shows a spinner in place of its label while saving;
- on success, refreshes the displayed submission (including the new `Updated At`) and shows a "Status
  updated successfully." snackbar;
- on failure, shows a snackbar with the backend's error message.

**Load states:**
- Loading: spinner, no content.
- Not found (backend 404): "Submission not found." card with a "Back to Dashboard" button.
- Other load error: "Something went wrong loading this submission." card with a "Back to Dashboard"
  button.

"Back to Dashboard" (always visible once loaded) returns to `/admin/dashboard`.

---

## Not yet built

Per the roadmap (`java-spring-boot-angular-project-prompts.md`), still outstanding:

- **Phase 16/17 — Authentication:** no login page, no JWT, no route guards. The admin pages above are
  currently open to anyone with the URL.
- Everything from Phase 18 onward (tests, Docker, deployment, security/architecture review) is
  process/infra work, not additional pages.
