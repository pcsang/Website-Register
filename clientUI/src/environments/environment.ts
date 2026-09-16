/**
 * Production environment configuration.
 * `apiBaseUrl` is overridden per-environment via Angular's `fileReplacements` build mechanism
 * (see angular.json) — this file's value is used for the default/production build.
 *
 * REQUIRED before deploying to Vercel (Phase 24): replace the placeholder below with the real
 * Render backend URL from Phase 23 (e.g. `https://register-backend.onrender.com`), then rebuild.
 * This value is baked into the JS bundle at build time — there is no runtime env var for it, so a
 * URL change means editing this file and redeploying, not a Vercel dashboard setting.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://REPLACE_WITH_YOUR_RENDER_BACKEND_URL.onrender.com'
};
