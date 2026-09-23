# Plan 3 — Feature Roadmap (Short-Term + Long-Term)

**What this is:** a scoped backlog of feature ideas suggested after Phase 27 (SePay payments), each
sized and sequenced, following the same pattern as `plan-1-ui-reskin-ngan-han.md`/
`plan-2-full-redesign-driveup.md` — a proposal to react to and pick from, not a committed roadmap. Nothing
here is implemented yet; none of it is numbered into the main roadmap (`java-spring-boot-angular-project-
prompts.md`) or `CHECKLIST.md` until picked up, the same way Phase 27 itself started as an out-of-roadmap
idea before being scoped and built.

For the current system shape these ideas build on, see
[`docs/architecture-diagram.md`](../architecture-diagram.md),
[`docs/backend-specification.md`](../backend-specification.md), and
[`docs/ui-specification.md`](../ui-specification.md).

---

## Decisions needed before starting any of these

| # | Item | Question | Recommendation |
|---|---|---|---|
| 1 | Branch divergence fix | Fast-forward `main` to `develop`, or repoint Render/Vercel to deploy from `develop` directly? | Repointing the deploy target is lower-risk (no force-push/rewrite of `main`'s history) and faster — but it's the user's infrastructure call, flagged 3 times now across Phases 25/26/this session. |
| 2 | Registration/payment notifications | Email, Zalo OA, SMS, or some combination? | **Email first** — no new paid integration, `spring-boot-starter-mail` is a one-dependency addition, and it covers both the registration-confirmation and payment-confirmation cases. Zalo OA is worth a follow-up if the user's students are more reachable there than by email (common for VN consumer businesses) — but it needs its own account/API setup, so treat as a separate decision, not bundled into the first pass. |
| 3 | Stuck-payment admin action | A dedicated "cancel"/"force-approve" button, or is a documented direct-DB procedure acceptable for now? | Given payment volume is presumably low at this app's stage, a **minimal admin action** (cancel only, so a fresh QR can be generated) is enough; deliberately skip "force-approve a below-floor payment" as a UI feature — that's a rare, high-stakes action better done deliberately via DB, not a button someone can misclick. |
| 4 | CAPTCHA/bot protection | Full CAPTCHA (reCAPTCHA/hCaptcha, needs an external account + API key) or a honeypot field (zero new accounts, catches only unsophisticated bots)? | **Honeypot first**, per Phase 25's original recommendation — only escalate to a real CAPTCHA if spam is an observed problem, not a hypothetical one. |
| 5 | Class scheduling | Is this "add a start time + simple session list" or a full calendar with recurring sessions, instructor availability, and attendance tracking? | Needs its own decision/scoping pass once picked up — the two ends of that range are a Small feature and a Large one. Not scoped in detail here; see its entry below for why. |
| 6 | Role-based admin access | How many roles are actually needed (e.g. just "admin" vs "branch staff"), and is multi-branch operation even a near-term reality? | Don't build this speculatively — it's real effort (new `AdminRole` concept, endpoint-level authorization changes, UI to manage it) for a need that may not exist yet. Revisit only when there's a second real admin user to configure. |

---

## Short-term

### 1. Fix the `main`/`develop` branch divergence
**Why:** Critical finding, open since Phase 26, directly undermines confidence that anything shipped since
is actually live. **Not a feature** — infrastructure hygiene — but it's listed first because everything
else in this roadmap assumes `develop`'s code is what's actually running in production.
**Scope:** either a `git` fast-forward of `main`, or reconfiguring Render's/Vercel's deploy branch setting
(no code change either way).
**Effort:** XS (minutes), but needs decision #1 above first.

### 2. Registration + payment notifications (email)
**Why:** right now, nothing tells a student their registration was received or their payment cleared —
the admin has to notice a new row and the student has to keep refreshing/asking. This is the most visible
gap right after shipping payments.
**Scope:**
- Add `spring-boot-starter-mail`, an SMTP config block (`app.mail.*`, env-var backed, same pattern as
  every other credential in this app), and a small `NotificationService`.
- Two triggers: `SubmissionService.createSubmission` (registration received) and
  `PaymentService.handleSepayWebhook`'s PAID branch (payment confirmed) — both already the right place to
  add a side-effect, following the existing `@Transactional` service-method pattern.
- Needs `Submission.email` to be present to send anything — it's already optional server-side (per
  `docs/backend-specification.md`), so a submission with no email just skips the notification, logged, not
  an error.
- Simple text/HTML templates, no templating engine dependency needed for two fixed messages.
**Effort:** S–M. **Depends on:** decision #2 (channel).

### 3. Admin action to cancel a stale pending payment
**Why:** Phase 27 shipped with no way to clear a `PENDING` payment other than a direct database edit —
flagged as a known gap in both `CHECKLIST.md`'s Phase 27 entry and `docs/ui-specification.md`'s "Not Yet
Implemented" list.
**Scope:**
- Backend: `PATCH /api/admin/submissions/{id}/payment/cancel` (or similar) →
  `PaymentService.cancelPendingPayment`, sets `status = CANCELLED`, same `ResourceNotFoundException`/
  `@Transactional` pattern as every other mutation in that service.
- Frontend: a "Huỷ yêu cầu" button on the Submission Detail payment card, shown only when
  `status === 'PENDING'`, same confirm-then-snackbar pattern already used for status updates.
**Effort:** S. **Depends on:** decision #3 (scope — cancel-only, not force-approve).

### 4. Honeypot field on the public registration form
**Why:** Phase 25's deferred spam-protection recommendation — the lowest-effort version of "some bot
protection," not yet built.
**Scope:** an extra hidden form field real users never fill in; if it arrives non-empty, silently accept
the request but drop it (return `201` as normal, don't actually persist) — so a bot scraping for a
success/failure signal can't tell it didn't work. No new dependency, no external account.
**Effort:** XS. **Depends on:** decision #4 (confirm honeypot over CAPTCHA).

### 5. Frontend test coverage for `auth.guard.ts` / `auth.interceptor.ts`
**Why:** carried over from Phase 26's "Should improve" finding — small, security-relevant files with zero
real test assertions today (still true as of the Phase 27 code review).
**Scope:** real Jasmine specs for the guard's redirect-when-unauthenticated behavior and the
interceptor's bearer-token-attachment behavior, mirroring the real-assertion style already established in
`submission-detail.component.spec.ts`'s Phase 27 additions.
**Effort:** S. **Depends on:** nothing — can be picked up anytime.

---

## Long-term

### 6. Class scheduling
**Why:** `Course` today is a catalog entry (name, price, seat count, one `startDate`) — there's no concept
of individual class sessions, instructors' availability, or attendance.
**Scope (needs its own decision pass — not fully scoped here):** ranges from "add a few more date/time
fields to `Course`" (Small) to "a real session/attendance domain with a calendar UI" (Large). The original
DriveUp mockup's "week-strip mini-calendar" (never built, see `PAGES.md`'s "Not yet built") is the smallest
version of this; a full scheduling system is a much bigger initiative deserving its own `plan-4-*.md` when
picked up, the same way Plan 2 got its own doc before the DriveUp domain adoption started.
**Effort:** M–XL depending on scope. **Depends on:** decision #5.

### 7. Real pass-rate tracking
**Why:** the Overview page's "Pass Rate" KPI is either blank or a manually-typed number today (per
`docs/backend-specification.md` §8.3/§12) — there's no exam-result data anywhere in the system.
**Scope:** a minimal `ExamResult`-style record per student (pass/fail + date) would be enough to compute a
real percentage; deliberately avoid building a full exam-management domain (scheduling exams, multiple
attempts, etc.) unless that's an actual expressed need beyond "show a real number here."
**Effort:** M. **Depends on:** nothing blocking, but low priority until scheduling (idea 6) exists, since
exam results logically follow a class session existing.

### 8. Student self-service lookup
**Why:** today a student has no way to check their own registration/payment status except calling/asking
the school directly.
**Scope:** the lowest-effort version is a public, unauthenticated lookup by phone number + a short code
(not a full login/account system — this app has never had student accounts, and building one is a much
bigger scope decision, explicitly called "not in scope" back in Plan 2). Returns just enough to be useful
(status, payment status) without exposing other students' data — needs care around what "a short code"
actually is (e.g. reuse the existing `paymentCode`-style approach, or a dedicated lookup token) so it can't
be brute-forced into an enumeration/data-leak risk.
**Effort:** M. **Depends on:** a real security design pass before building (this is the one idea on this
list with a genuine data-exposure risk if done carelessly) — treat as its own small decision doc, not a
quick add-on.

### 9. Role-based admin access
**Why:** today there's exactly one flat `ROLE_ADMIN` — fine for a single-operator system, a real gap the
moment a second admin or multi-branch staff exists.
**Scope:** a new `AdminRole` concept on `AdminUser`, `@PreAuthorize`/`hasRole` changes across
`/api/admin/**` endpoints to differentiate what each role can do, and admin UI to manage other admin
accounts (today, admin accounts are only created via the `AdminUserSeeder` at first boot — there's no
"create another admin" flow at all).
**Effort:** L. **Depends on:** decision #6 — don't build ahead of an actual second-admin need.

---

## Suggested sequencing

1. Branch divergence fix (idea 1) — do this regardless of what else gets picked, it's not really optional.
2. Honeypot (idea 4) and the payment-cancel admin action (idea 3) — both small, independent, no shared
   dependency, can happen in either order or in parallel.
3. Notifications (idea 2) — slightly bigger, but the highest visible payoff right after Phase 27.
4. Auth guard/interceptor tests (idea 5) — anytime, doesn't block or get blocked by anything else.
5. Everything under Long-term — pick up individually, each starting with its own short decision pass
   (like this document did for Plan 2) before any code is written.

## Explicitly not in this plan

- Any payment gateway beyond SePay (card processing, other e-wallets) — not requested, and VietQR bank
  transfer already covers the stated business need.
- A native mobile app — no signal this is needed yet; the Angular app is responsive.
- Multi-tenant (multiple driving schools on one deployment) — this system is single-tenant by design
  throughout; would be a different product, not a feature.
