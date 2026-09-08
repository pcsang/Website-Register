/**
 * Production environment configuration.
 * `apiBaseUrl` is overridden per-environment via Angular's `fileReplacements` build mechanism
 * (see angular.json) — this file's value is used for the default/production build.
 * No production backend has been deployed yet (deployment is a later roadmap phase), so this
 * still points at the local dev backend; update it once a production API URL exists.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080'
};
