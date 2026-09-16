# Deploying the Frontend to Vercel (Phase 24)

Guide for taking the Angular app (`clientUI/`) from "builds locally" to a live site on Vercel, talking to
the Render backend from Phase 23:

```
GitHub  →  Vercel  →  Angular (static SPA)  →  Render backend  →  Neon PostgreSQL
```

This is a **manual, one-time walkthrough** — it deploys nothing on your behalf. You'll need your own
Vercel account; this doc tells you exactly what to click and what to type.

**Do this after Phase 23** (backend deployed to Render) — step 1 below needs the real Render URL.

---

## 0. Before you start

**Two things were required and have already been made ready:**

1. **`clientUI/src/environments/environment.ts`** (the production build's config) had its `apiBaseUrl`
   pointing at `http://localhost:8080` — hardcoding `localhost` into a production bundle would mean the
   deployed site tries to call your own machine. Changed to a placeholder:
   ```ts
   apiBaseUrl: 'https://REPLACE_WITH_YOUR_RENDER_BACKEND_URL.onrender.com'
   ```
   **You must edit this** to your real Phase 23 Render URL before building for deployment (step 2 below)
   — see [why there's no Vercel env var for this instead](#note-why-this-isnt-a-vercel-environment-variable).

2. **New `clientUI/vercel.json`**:
   ```json
   {
     "buildCommand": "npm run build",
     "outputDirectory": "dist/client-ui/browser",
     "rewrites": [
       { "source": "/(.*)", "destination": "/index.html" }
     ]
   }
   ```
   This does two things:
   - Tells Vercel exactly where the build output lands. Angular's current build system (the `application`
     builder) nests output under a `browser/` subfolder (`dist/client-ui/browser/`, confirmed by actually
     running `npm run build` and inspecting the result) — Vercel's automatic Angular detection has
     historically expected the older `dist/client-ui/` (no `browser/` subfolder) layout, so leaving this
     unset is a known way to get a blank/broken deploy.
   - The `rewrites` rule is the **SPA fallback**: this app is a pure client-side-routed single-page app
     with no server-rendered per-route HTML files. Without this rule, a hard refresh (or a shared link) to
     e.g. `/admin/dashboard` 404s at Vercel's static-file layer *before* Angular's own router ever loads,
     because no physical `dist/client-ui/browser/admin/dashboard/index.html` exists. This rule tells Vercel
     "serve `index.html` for literally any path," and Angular's `Router` (already configured with all four
     routes — `/form`, `/admin/login`, `/admin/dashboard`, `/admin/submissions/:id`) takes over from there
     client-side. This satisfies requirements 5 and 6 with no application code changes needed.

Verified: `npm run build` succeeds with the placeholder URL in place, and the placeholder string is
confirmed present in the built JS bundle (i.e. the value really is what ends up in the deployed site,
proving the build/config wiring is correct) — output at `dist/client-ui/browser/`.

---

## 1. Get your Render backend URL (from Phase 23)

You need the real `https://<your-service>.onrender.com` URL from your completed Phase 23 deployment.

---

## 2. Set the real production API URL and rebuild

Edit `clientUI/src/environments/environment.ts`:

```ts
export const environment = {
  production: true,
  apiBaseUrl: 'https://<your-actual-render-service>.onrender.com'   // <-- your real URL, no trailing slash
};
```

Commit this change (this is a public URL, not a secret — it's fine to commit, unlike `backend`'s
`DB_PASSWORD`/`JWT_SECRET`). You'll do this once now, and again later only if your Render URL ever changes.

#### Note: why this isn't a Vercel environment variable

Angular's `environment.ts`/`environment.development.ts` files are plain TypeScript, resolved at **build
time** via `fileReplacements` (see `angular.json`) — whatever string is in the file becomes a literal
string baked into the shipped JS bundle. There's no server-side runtime reading a `process.env` value for
a static SPA the way a Node/Next.js app might. Setting a Vercel "Environment Variable" for this wouldn't
actually reach the browser bundle without extra build tooling this project doesn't have (and doesn't need,
per this repo's "no unnecessary dependencies" convention) — so the simplest correct approach, consistent
with how the file already documented itself before this phase, is: edit the file, commit, redeploy.

---

## 3. Create the Vercel project

1. From the Vercel dashboard: **Add New → Project**.
2. **Connect Vercel to GitHub** (requirement 7) if you haven't already, and import this repository.
3. **Root Directory**: set to `clientUI` (this repo has both `backend/` and `clientUI/` — Vercel needs to
   know the Angular app doesn't live at the repo root).
4. Framework Preset: Vercel should auto-detect **Angular**. If it offers to override Build Command /
   Output Directory with its own guesses, let `vercel.json` (already committed, step 0) win — it's more
   precise than the auto-detected defaults for this Angular version's output layout.
5. No environment variables are needed for this project (see the note in step 2) — leave that section
   empty unless you add something unrelated later.

---

## 4. Deploy

Click **Deploy**. Vercel will:
1. Pull the repo, `cd` into `clientUI` (Root Directory).
2. Run `npm install`, then `npm run build` (from `vercel.json`).
3. Serve the contents of `dist/client-ui/browser/` as a static site, with the SPA rewrite rule applied to
   every request.

You'll get a URL like `https://your-project.vercel.app` (plus a unique URL per deploy/branch — the
production domain is the one to use going forward).

**Redeploying from GitHub:** like Render, Vercel auto-deploys on every push to the connected
branch by default (and creates a preview deployment for every PR/branch, separate from production) — no
manual step needed for routine updates.

---

## 5. Close the loop: update the backend's CORS setting

The backend's `ALLOWED_ORIGINS` env var (set on Render in Phase 23) needs to match this Vercel URL exactly,
or every API call from the deployed frontend will fail CORS. This is the one circular dependency between
Phases 23 and 24 — you can only do this step now that you know the real Vercel URL:

1. Go back to the Render dashboard → your backend service → **Environment**.
2. Update `ALLOWED_ORIGINS` to your production Vercel URL, e.g. `https://your-project.vercel.app` (exact
   scheme + host, no trailing slash — multiple origins can be comma-separated if you also want to keep
   `http://localhost:4200` allowed for local dev against the prod backend).
3. Save — Render restarts the service automatically to pick up the change. Wait for it to show healthy
   again before testing.

---

## 6. Verify

Open your Vercel URL in a browser and walk through each of these (requirement 12):

| Check | How |
|---|---|
| **Public form submission** | Go to `/form`, submit a valid entry, confirm the success snackbar (not an error) — this proves the frontend can reach the Render backend at all. |
| **Admin login** | Go to `/admin/login`, log in with the seeded admin credentials from Phase 23 (`ADMIN_USERNAME`/`ADMIN_PASSWORD`), confirm redirect to the dashboard. |
| **Admin dashboard** | Confirm the summary cards and submissions table load real data (including the entry you just submitted). |
| **Submission detail** | Click "View" on a row, confirm the detail page loads that submission's data. |
| **Status update** | Change the status on the detail page, save, confirm the success snackbar and the new status persists on reload. |
| **Refresh-on-deep-route** | While on `/admin/dashboard` or `/admin/submissions/:id`, hard-refresh the browser (not just client-side navigation) — confirms the `vercel.json` SPA rewrite is actually working, not just that initial navigation works. |

If the public form works but admin login doesn't, or vice versa, that's a useful signal — see
troubleshooting below.

---

## Troubleshooting

**Frontend can't reach the backend at all** (every request fails, network tab shows a failed/refused
connection rather than a CORS error specifically):
- Confirm `environment.ts`'s `apiBaseUrl` was actually updated and redeployed — check the deployed bundle
  (browser DevTools → Network tab → look at what URL the failing request actually went to).
- Confirm the Render service is actually up (`curl https://<render-url>/api/health` directly).
- Remember Render's free tier cold-starts after inactivity — the very first request after idle time can be
  slow enough to look like a failure; retry once.

**CORS errors** (browser console explicitly mentions CORS / blocked by CORS policy):
- This means the request *reached* the backend but got rejected — almost always `ALLOWED_ORIGINS` on
  Render doesn't exactly match the Vercel URL (see step 5). Check for a scheme mismatch (`http` vs
  `https`), a trailing slash, or a stale value from before you knew the real Vercel URL.
- Confirm you waited for Render to finish restarting after changing `ALLOWED_ORIGINS` before retesting.

**Deep-link/refresh 404s** (navigating via in-app links works, but refreshing or directly visiting
`/admin/dashboard` shows a 404 page):
- `vercel.json`'s `rewrites` rule isn't being applied — confirm the file is actually at `clientUI/vercel.json`
  (not the repo root, since Vercel's Root Directory is `clientUI`) and was included in the deployed commit.
- Confirm Vercel didn't silently override it with auto-detected framework settings — recheck the project's
  Build & Development Settings in the Vercel dashboard match `vercel.json`.

**Blank page / JS errors on load, but the build succeeded:**
- Check the Output Directory setting matches `dist/client-ui/browser` exactly — if Vercel serves from
  `dist/client-ui/` (missing the `browser/` subfolder), it'll serve either nothing or a directory listing
  instead of `index.html`.
- Check browser DevTools console for the actual error — a CSP/mixed-content error (`https` page calling an
  `http` API) would show here distinctly from a CORS error.

---

## What's still open

- Actually creating the Vercel project and connecting it — not done from this environment (no
  credentials).
- This depends on Phase 23 already being live — if you haven't deployed the backend yet, do that first.
- Phase 25 (Production Security Review) hasn't happened yet.
