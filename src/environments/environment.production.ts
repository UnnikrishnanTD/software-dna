/**
 * Production configuration.
 *
 * The frontend (Cloudflare Pages) and backend (Railway) are deployed as two
 * separate origins, so `apiBaseUrl` must be the backend's full public URL —
 * unlike local development, this cannot be a same-origin relative path.
 *
 * Deploy order matters: the backend must be deployed first so its Railway
 * URL is known, then this placeholder is replaced with that URL before the
 * frontend is built and deployed.
 */
export const environment = {
  production: true,
  useBackend: true,
  // Replace with the Railway backend's public URL once deployed, e.g.
  // 'https://software-dna-backend-production.up.railway.app/api'
  apiBaseUrl: 'REPLACE_WITH_RAILWAY_BACKEND_URL/api',
  analysisPollIntervalMs: 1000,
} as const;
