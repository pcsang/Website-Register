# Pages

Describes every page currently implemented in the Angular app (`clientUI/`), what it's for, what's on
it, and how it behaves. Routes are defined in `clientUI/src/app/app.routes.ts`.

> **Auth status:** admin routes require a valid admin login (JWT). Anyone can reach `/admin/login`, but
> `/admin/overview`, `/admin/students`, `/admin/courses`, and `/admin/submissions/:id` redirect to the
> login page if you're not signed in.

| Route | Component | Audience |
|---|---|---|
| `/` | `LandingPageComponent` | Public |
| `/form` | redirects to `/` | — (old link, kept for compatibility) |
| `/admin/login` | `LoginComponent` | Public |
| `/admin/overview` | `OverviewComponent` | Admin (guarded) |
| `/admin/students` | `StudentsComponent` | Admin (guarded) |
| `/admin/courses` | `CoursesComponent` | Admin (guarded) |
| `/admin/dashboard` | redirects to `/admin/overview` | — (old link, kept for compatibility) |
| `/admin/submissions/:id` | `SubmissionDetailComponent` | Admin (guarded) |

---

## Public

### Landing Page — `/`

**File:** `clientUI/src/app/public/landing/`

The public-facing entry point of the site — a single scrolling marketing + registration page for
"DriveUp," a driving-school brand, with 8 sections in order:

1. **Nav bar** — sticky, DriveUp logo, links that scroll down to the sections below (Ưu điểm/Khoá học/
   Quy trình/Đánh giá), a hotline number, and a "Đăng ký ngay" button that scrolls to the registration form.
2. **Hero** — headline, short pitch, two buttons ("Đăng ký học ngay" / "Xem khoá học"), and a row of 3
   stats (students graduated, first-time pass rate, branch count).
3. **Ưu điểm (Features)** — 4 fixed cards explaining why to choose DriveUp (experienced teachers, modern
   practice cars, flexible schedule, full paperwork support).
4. **Khoá học (Courses)** — a pricing grid showing **real courses loaded from the backend**
   (`GET /api/courses`), not fixed text. Whichever course is license class **B2** gets a highlighted
   "Phổ biến nhất" (most popular) treatment. Each card shows the price, duration, practice hours, and a
   "Chọn khoá học" button that scrolls down and pre-selects that license class in the registration form.
5. **Quy trình (Process)** — 4 fixed steps from inquiry to first day of class.
6. **Đánh giá (Reviews)** — 3 fixed testimonial cards with star ratings.
7. **Đăng ký tư vấn (Registration form)** — the actual signup form:

   | Field | Required | Validation |
   |---|---|---|
   | Họ và tên (Full name) | Yes | max 200 chars |
   | Số điện thoại (Phone) | Yes | Vietnamese mobile format (`0` or `+84` + `3/5/7/8/9` + 8 digits) |
   | Email | Yes | valid email format, max 255 chars |
   | Hạng bằng muốn học (License class) | Yes | one of B1 / B2 / C |

   These validators are **stricter** than what the backend actually requires (phone/email are optional
   server-side) — deliberately, per this page's own design. Submitting posts to
   `POST /api/submissions`, resolving the chosen license class to a real course ID behind the scenes. On
   success, the form is replaced with a "Đăng ký thành công!" confirmation — no page navigation.
8. **Footer** — dark, 4 columns (brand blurb, course links, company links, contact info) + copyright line.

**Behavior notes:**
- Submit button shows a spinner and disables while the request is in flight.
- On failure, a snackbar shows the backend's error message (Vietnamese fallback text if the response
  shape is unexpected).
- A submission made here shows up immediately in the admin Students page.

---

## Admin

### Admin Login — `/admin/login`

**File:** `clientUI/src/app/admin/login/`

A simple centered card: Username + Password fields, both required, and a "Login" button. On success,
redirects to `/admin/overview`. On failure, shows the backend's error message via a snackbar (generic
"Invalid username or password" fallback).

### Admin Layout (shared shell for all admin pages below)

**File:** `clientUI/src/app/admin/layout/`

Every admin page except Login shares this shell:
- A dark **sidebar** (left, collapses to an off-canvas drawer with a hamburger toggle on narrow screens)
  with 3 links: **Overview**, **Students**, **Courses**.
- A **topbar** with a search box (only active on the Students page — hidden elsewhere), the current
  admin's initial + username, and a Logout button.

### Overview — `/admin/overview`

**File:** `clientUI/src/app/admin/overview/`

The admin landing page after login — a snapshot of the business, not a data-entry page.

- **4 KPI cards**: New Students This Month, Revenue This Month (an *estimate*, not real payment data),
  Open Courses, Pass Rate (shows `—` until an admin configures it — it's not calculated from real exam
  data, there is no exam-tracking in this system).
- **Registrations by Month** — a simple bar chart (plain colored bars, no charting library) of the last
  6 months of signups.
- **Upcoming Courses** — the next few courses by start date, each showing seats filled vs. total, with a
  "View all" link to the Courses page.
- **Recent Registrations** — the newest handful of submissions, with a "View all" link to the Students
  page.

Two independent loading spinners — a failure loading one section doesn't block the other.

### Students — `/admin/students`

**File:** `clientUI/src/app/admin/students/`

The full list of everyone who has registered — search, filter, and manage them here. (This page used to
be called "Dashboard" and combined the KPI cards shown above with this table; the cards moved to
Overview and this page is now purely the list.)

**Toolbar:**
- **Search box** (in the topbar) — free-text, matched against name/email/phone; debounced 300ms.
- **Status filter** — All / Chờ tư vấn / Đã xác nhận / Đang học / Đã tốt nghiệp (`PENDING_CONSULTATION` /
  `CONFIRMED` / `IN_PROGRESS` / `GRADUATED`).
- **Course filter** — All, or a specific course.

**Table:** Full Name, Email, Phone, Status (color-coded badge), Created At, a "View" action per row.
Pagination: page sizes 10/20/50, first/last buttons. "No submissions found." empty state.

### Courses — `/admin/courses`

**File:** `clientUI/src/app/admin/courses/` (create/edit dialog: `admin/courses/course-form-dialog/`)

Manage the course catalog.

**Toolbar:** License class filter (All/B1/B2/C) and a branch filter (populated from whatever branch
values currently exist). No status filter — seat availability is computed, not a stored field you can
filter by directly.

**Table:** course name, start date, teacher, a seats-filled progress bar (green = plenty of room, amber =
filling up, red = full — computed from real registered-student counts, never a stale stored number), and
an Edit action.

**"+ Thêm khoá học"** opens a dialog with a form for every course field (name, license class, price,
duration, practice hours, description, branch, teacher, total seats, start date) — the same dialog
handles both creating a new course and editing an existing one.

### Submission Detail — `/admin/submissions/:id`

**File:** `clientUI/src/app/admin/submission-detail/`

Detail view for a single submission, with the ability to change its status.

**Displayed fields:** Full Name, Email, Phone, Message, Status, Created At, Updated At (blank optional
fields show as `—`).

**Status update:** a dropdown pre-filled with the current status (Chờ tư vấn / Đã xác nhận / Đang học /
Đã tốt nghiệp), plus an "Update Status" button that:
- is disabled while saving, while nothing's loaded yet, or when the selected value equals the current
  status (no-op guard);
- on success, refreshes the displayed submission and shows a success snackbar;
- on failure, shows the backend's error message.

**Load states:** loading spinner; "Submission not found." (404) with a back link; a generic load-error
message with a back link. "Back to Students" returns to `/admin/students`.

---

## Not yet built

- **Week-strip mini-calendar** on the Courses page (from the original design mockup) — skipped as a first
  pass; the table, filters, and create/edit dialog are the parts that matter.
- **404 page** — there's no wildcard route for an unmatched URL.

## Live

- **Frontend:** `https://website-register-roan.vercel.app` (Vercel, Phase 24).
- **Backend:** `https://backed-website-register.onrender.com` (Render, Phase 23).
