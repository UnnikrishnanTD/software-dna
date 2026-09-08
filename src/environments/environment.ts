/**
 * Development configuration.
 *
 * `useBackend` is the switch described in the integration plan: with it off the
 * application runs entirely on `MockAnalysisService`, and with it on every
 * screen reads from the Spring Boot API. Both satisfy the same
 * `AnalysisService` contract, so no component changes either way.
 */
export const environment = {
  production: false,
  /** Set to false to work offline against the sample dataset. */
  useBackend: true,
  apiBaseUrl: 'http://localhost:8080/api',
  /** How often the analysis screen polls while a scan is running. */
  analysisPollIntervalMs: 800,
} as const;
