/**
 * Production environment configuration.
 * `apiBaseUrl` is overridden per-environment via Angular's `fileReplacements` build mechanism
 * (see angular.json) — this file's value is used for the default/production build.
 *
 * Points at the live Phase 23 Render deployment. This value is baked into the JS bundle at build
 * time — there is no runtime env var for it, so a URL change means editing this file and
 * redeploying, not a Vercel dashboard setting.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://backed-website-register.onrender.com'
};
