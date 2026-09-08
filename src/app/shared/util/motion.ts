/** True when the user has asked the system to minimise motion. */
export function prefersReducedMotion(): boolean {
  return (
    typeof matchMedia === 'function' &&
    matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/** Ease-out cubic — the curve used for every value transition in the app. */
export function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3);
}
