# SePay Payment Workflow Guide (Phase 27)

Operational guide for the VietQR + webhook tuition-payment feature: how the flow works end to end, how to
configure the real SePay account against this app, how to test it locally before it can reach a live
webhook, and how to troubleshoot the failure modes that don't show up until real money moves.

```
Admin generates QR  →  Student scans + transfers in their banking app  →  SePay detects the transfer on
the linked bank account  →  SePay POSTs a webhook to this backend  →  Payment flips to PAID
```

This is **not** a deploy-it-for-you script — SePay dashboard configuration is a manual, you-must-do-this
step (same as Render/Neon account setup in the other guides in this folder). This doc tells you exactly
what to configure and how to verify it.

---

## 0. What already exists in code (Phase 27)

- `Payment` entity/table, `POST`/`GET /api/admin/submissions/{id}/payment` (admin-only, generates or
  fetches a VietQR payment request for a submission's tuition), and a public `POST /api/webhooks/sepay`
  that SePay calls when a transfer lands.
- A "Thanh toán" section on the admin Submission Detail page (`/admin/submissions/:id`) — "Tạo mã QR thanh
  toán" button, QR image, amount, payment code, status badge, manual "Làm mới trạng thái" refresh.
- Payment status (`PENDING`/`PAID`/`CANCELLED`) is **decoupled** from `Submission.status` — a payment being
  marked `PAID` never auto-changes the submission's own status; the admin still advances that manually.
- No card processing, no outbound call to any SePay API to "generate" a QR — the QR image URL is built
  locally from `img.vietqr.io`'s public static-image convention (bank code + account number + amount +
  content), server-side, in `PaymentMapper`.

See `CHECKLIST.md`'s Phase 27 entry for the full implementation log, and
`D:\Home\Website-Register\backend\src\main\java\com\register\backend\service\PaymentService.java` /
`SepayWebhookController.java` / `PaymentMapper.java` for the actual code this guide describes.

---

## 1. ⚠️ Verify against your live SePay dashboard first

This code was written **without live internet access to SePay's docs**, based on commonly documented SePay
conventions. Three specifics are assumptions, not confirmed facts, and must be checked against your actual
SePay dashboard before relying on this in production:

| Assumption in code | Where | What to check in the SePay dashboard |
|---|---|---|
| Webhook auth is an `Authorization: Apikey <secret>` header | `SepayWebhookController.java` | The exact header name and prefix SePay lets you configure/sends |
| Webhook JSON fields are `id, gateway, transactionDate, accountNumber, content, transferType, transferAmount, referenceCode` | `SepayWebhookRequest.java` | The real field names in a sample webhook payload (SePay's dashboard usually has a "test webhook" or sample-payload feature) |
| VietQR bank code is SePay/VietQR's short bank code, not the numeric BIN | `application.yml` → `SEPAY_BANK_CODE` | Which format `img.vietqr.io` expects for your specific bank |

If any of these differ, the fix is small and localized (adjust the header string in
`SepayWebhookController`, the field names in `SepayWebhookRequest`, or the `SEPAY_BANK_CODE` value) — it
does not require redesigning anything else in this feature.

---

## 2. SePay dashboard setup

1. Sign in to your SePay account (already linked to your business/personal bank account — confirmed
   available before this phase was implemented).
2. Find your linked account's details and note down:
   - **Bank account number** — becomes `SEPAY_BANK_ACCOUNT_NUMBER`.
   - **Bank code** — becomes `SEPAY_BANK_CODE` (verify the exact format per the table above).
   - **Account holder name** (as it appears on the bank account) — becomes `SEPAY_ACCOUNT_HOLDER_NAME`.
3. In SePay's webhook/integration settings, register this backend's webhook URL:
   ```
   https://<your-render-service>.onrender.com/api/webhooks/sepay
   ```
   (use your real Render URL — see `render-backend-deployment.md` for what that looks like).
4. Configure SePay's webhook authentication with a secret of your choosing (a long random string — e.g.
   `openssl rand -base64 48`, same approach already used for `JWT_SECRET`). This becomes
   `SEPAY_WEBHOOK_SECRET` — **do not reuse** any other secret already used in this app (JWT secret, admin
   password, DB password). If SePay's dashboard asks for a header name/format rather than just a raw
   secret, make sure what you configure there matches what `SepayWebhookController` expects (see §1) —
   adjust one side or the other so they agree.
5. If SePay offers a "send test webhook" feature, use it after deploying (§4 below) to sanity-check
   connectivity before relying on a real bank transfer.

---

## 3. Environment variables

Add these alongside the existing ones on Render's **Environment** tab (see
`render-backend-deployment.md` §3 for how that screen works) — never commit real values to this repo:

| Variable | Value | Notes |
|---|---|---|
| `SEPAY_WEBHOOK_SECRET` | the secret you configured in SePay's dashboard | **Required in prod** — `application-prod.yml` has no fallback, so the app now refuses to start with this unset (Phase 27's fail-fast fix, same pattern as `JWT_SECRET`/`ADMIN_PASSWORD`) |
| `SEPAY_BANK_ACCOUNT_NUMBER` | your linked bank account number | From §2 |
| `SEPAY_BANK_CODE` | your bank's VietQR code | From §2 — verify format per §1 |
| `SEPAY_ACCOUNT_HOLDER_NAME` | the account holder's name | From §2 — shown to students on the QR |
| `SEPAY_QR_TEMPLATE` | `compact2` (default, optional) | VietQR image layout — only set if you want a different one (`compact`, `qr_only`, etc.) |

Local dev (`backend/src/main/resources/application.yml`) falls back to a placeholder secret
(`dev-only-insecure-sepay-secret-CHANGE-ME`) and blank bank details if these env vars aren't set — fine for
running the app locally without real payment testing, but a QR generated with blank bank details won't be
scannable, and the webhook will accept the placeholder secret. Set real values in a local `.env`
(already `.gitignore`d) if you want to test the full flow locally.

---

## 4. Testing locally before going live

SePay's servers can't reach `localhost` — to receive a real webhook delivery during local development you
need a public tunnel:

```powershell
# one-time: install ngrok (or any similar tunnel tool), then:
ngrok http 8080
```

Take the `https://<random>.ngrok-free.app` URL ngrok gives you and register
`https://<random>.ngrok-free.app/api/webhooks/sepay` as the webhook URL in SePay's dashboard *temporarily*
for testing (swap it back to the real Render URL before/when you deploy — ngrok URLs are not permanent).

**Without a tunnel**, you can still verify everything except the real SePay round-trip by simulating a
webhook call yourself:

```bash
# 1. Start Postgres + the app locally (see CLAUDE.md's local dev commands), log in as admin, and
#    generate a payment QR for a real submission via the admin UI or:
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your local admin password>"}' | jq -r .token)

curl -s -X POST http://localhost:8080/api/admin/submissions/1/payment \
  -H "Authorization: Bearer $TOKEN" | jq .
# note the returned "id" (e.g. 1) -> payment code is "DUP" + that id zero-padded to 6 digits, e.g. DUP000001

# 2. Wrong/missing auth -> 401
curl -i -X POST http://localhost:8080/api/webhooks/sepay \
  -H "Content-Type: application/json" \
  -d '{"id":1,"transferType":"in","content":"DUP000001","transferAmount":12000000}'

# 3. Correct auth, full expected amount -> 200, payment flips to PAID
curl -i -X POST http://localhost:8080/api/webhooks/sepay \
  -H "Authorization: Apikey dev-only-insecure-sepay-secret-CHANGE-ME" \
  -H "Content-Type: application/json" \
  -d '{"id":555,"transferType":"in","content":"CT DEN:123 DUP000001 hoc phi","transferAmount":12000000}'

# 4. Confirm
curl -s http://localhost:8080/api/admin/submissions/1/payment -H "Authorization: Bearer $TOKEN" | jq .status
# "PAID"
```

(Swap `dev-only-insecure-sepay-secret-CHANGE-ME` for your real `SEPAY_WEBHOOK_SECRET` if you've set one
locally.)

---

## 5. End-to-end verification against the real deployed backend

1. Confirm all five env vars from §3 are set on Render and the service has redeployed since (env var
   changes need a restart — same as `render-backend-deployment.md` §5 note).
2. Log in to the live admin UI, open a real submission's detail page, click "Tạo mã QR thanh toán".
3. Confirm the QR image actually renders (a blank/broken image means `SEPAY_BANK_ACCOUNT_NUMBER`/
   `SEPAY_BANK_CODE` are missing or wrong — see §1/§3).
4. Make a **small real transfer** (not the full tuition amount — see §6's amount-floor note) to the
   configured bank account, with the exact QR-generated content/payment code as the transfer memo — most
   banking apps auto-fill this correctly when scanning the QR, but double-check if typing it manually.
5. Within SePay's normal detection delay, check the SePay dashboard shows the transaction, then click "Làm
   mới trạng thái" on the Submission Detail page — status should flip to "Đã thanh toán".
6. If it doesn't flip within a reasonable time, work through the Troubleshooting section below.

---

## 6. How matching works (for troubleshooting)

- **Payment code**: `"DUP" + zero-padded payment id` (e.g. payment id `42` → `DUP000042`). This is what
  goes into the QR's transfer-content field, and what the webhook handler looks for in the incoming
  `content` string via a tolerant regex (`DUP` + optional whitespace + digits, case-insensitive) — it
  survives most bank-app content mangling (extra spaces, different casing, other text mixed in) but the
  digits themselves must appear intact.
- **Idempotency**: each webhook payload's own `id` is recorded once processed; a redelivery of the same
  `id` is a silent no-op (not an error) — safe against SePay retries (e.g. if Render's free-tier cold start
  delayed the first response past SePay's timeout).
- **Amount floor**: a transfer must be **at least 90% of the recorded tuition amount** to be auto-marked
  paid. A transfer below that floor leaves the payment `PENDING` for manual admin review instead — this is
  a deliberate anti-fraud guard (the payment code is a visible, sequential value on the QR, so a trivial
  transfer with a guessed/observed code must not be able to fake-pay a much larger amount). A transfer
  *above* the floor but not an exact match is still accepted (e.g. minor bank-fee-driven shortfalls) and
  logged for reference.
- **Non-`"in"` transfers** (outgoing transactions on the monitored account, if SePay reports those on the
  same webhook) and payloads whose `content` doesn't contain a recognizable code are both silently ignored
  — logged, not errored, so SePay always gets a `200` and doesn't retry a payload that will never resolve
  differently.

---

## Troubleshooting

**Webhook call returns 401:**
- `SEPAY_WEBHOOK_SECRET` (Render) doesn't match what SePay is actually sending — re-check both sides.
  Remember Render env var changes need a redeploy to take effect.
- Confirm the header name/scheme really is `Authorization: Apikey <secret>` on SePay's side (see §1) — if
  SePay uses a different header name entirely, `SepayWebhookController` needs a matching code change, not
  just a config change.

**Webhook returns 200 but the payment stays PENDING:**
- Check the transfer content actually contains the exact payment code (`DUPnnnnnn`) — if the student edited
  or didn't preserve the auto-filled QR content, the regex won't find it. Check Render's logs for a "could
  not extract a payment code from webhook content" warning to confirm this is what happened.
- Check whether the transferred amount was below the 90% floor (§6) — check logs for "well below expected
  payment amount". This is working as designed (manual review required), not a bug.
- Confirm you're looking at the right payment — `findFirstBySubmissionIdOrderByCreatedAtDesc` returns the
  *most recent* payment for a submission; if "Tạo mã QR thanh toán" was clicked more than once across
  different sessions in a way that created more than one payment (shouldn't normally happen — the endpoint
  is idempotent per-`PENDING`-payment), the code embedded in an old QR image someone still has open won't
  match the currently-displayed one.

**QR image doesn't render / looks broken:**
- `SEPAY_BANK_ACCOUNT_NUMBER` or `SEPAY_BANK_CODE` is unset or wrong — `img.vietqr.io` returns an error
  image (or nothing) for an invalid bank code/account combination. Test the constructed URL directly in a
  browser (copy it from the page's network tab or from the `qrImageUrl` field in the API response).

**Nothing happens at all (no webhook call ever arrives):**
- Confirm the webhook URL registered in SePay's dashboard is the real, current Render URL (not a stale
  ngrok tunnel left over from local testing, per §4).
- Confirm the bank transfer actually landed in the *same* account SePay is monitoring — a QR built with the
  wrong `SEPAY_BANK_ACCOUNT_NUMBER` would direct the student's money to the right-looking but
  differently-configured account, and SePay would have nothing to report.
- Render free-tier cold starts (~30-60s) can delay the first response to a webhook past SePay's own retry
  window in rare cases — idempotent redelivery handling (§6) means a retry is safe, but check whether SePay
  actually retries or gives up after one attempt (dashboard/docs-dependent).

---

## What's still open

- SePay dashboard configuration (§2) itself — a you-must-do-this-yourself step, not something this session
  can do on your behalf.
- The three unverified assumptions in §1 — confirm against the live dashboard/docs before treating this as
  production-ready for real money.
- No admin UI exists yet to manually cancel a stale `PENDING` payment or force-mark one paid after a
  below-floor transfer flagged for review (§6) — currently a direct-database operation if it comes up;
  add a small admin action for this later if manual review turns out to be a frequent need.
