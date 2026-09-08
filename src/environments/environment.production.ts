/**
 * Production configuration.
 *
 * The frontend (Netlify) and backend (Railway) are deployed as two separate
 * origins, so `apiBaseUrl` is the backend's full public URL rather than a
 * same-origin relative path.
 */
export const environment = {
  production: true,
  useBackend: true,
  apiBaseUrl: 'https://software-dna-production.up.railway.app/api',
  analysisPollIntervalMs: 1000,
} as const;
