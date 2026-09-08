/**
 * Helpers for values the backend may genuinely not have measured.
 *
 * The analysis engine reports null for "not measured" and never substitutes a
 * number, so the UI has to distinguish the two as well. A coverage of null is
 * "we could not run the tests"; a coverage of 0 would be "nothing is covered".
 * Rendering the first as the second is the exact failure these helpers exist
 * to prevent.
 */

/** True when a measurement is present, narrowing the type for the caller. */
export function isMeasured(value: number | null | undefined): value is number {
  return value !== null && value !== undefined && !Number.isNaN(value);
}

/** Formats a measurement for display, or an em dash when it is absent. */
export function formatMeasurement(
  value: number | null | undefined,
  options: { readonly suffix?: string; readonly decimals?: number } = {},
): string {
  if (!isMeasured(value)) return '—';
  const { suffix = '', decimals = 0 } = options;
  return `${value.toFixed(decimals)}${suffix}`;
}

/**
 * A numeric value for sorting and layout only.
 *
 * Never use this to display a figure: it substitutes a number where none was
 * measured, which is fine for deciding where to draw a mark and wrong for
 * telling a reader what the value is.
 */
export function forLayout(value: number | null | undefined, fallback = 0): number {
  return isMeasured(value) ? value : fallback;
}

/** Compares two possibly-absent measurements, sorting absent values last. */
export function compareMeasurements(
  a: number | null | undefined,
  b: number | null | undefined,
  direction: 'asc' | 'desc' = 'desc',
): number {
  const aMeasured = isMeasured(a);
  const bMeasured = isMeasured(b);
  if (!aMeasured && !bMeasured) return 0;
  if (!aMeasured) return 1;
  if (!bMeasured) return -1;
  return direction === 'desc' ? b - a : a - b;
}
