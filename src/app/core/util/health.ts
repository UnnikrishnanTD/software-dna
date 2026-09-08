import { HealthVerdict } from '../models/dna.model';
import { RiskLevel } from '../models/architecture.model';

/**
 * Single source of truth for turning a 0–100 score into a verdict and a
 * colour. Every visualisation in the product reads from here, so the DNA,
 * the graph, the hotspot map and the timeline all agree on what "healthy"
 * looks like.
 */

interface HealthBand {
  readonly min: number;
  readonly verdict: HealthVerdict;
  readonly label: string;
}

const BANDS: readonly HealthBand[] = [
  { min: 90, verdict: 'exemplary', label: 'Exemplary' },
  { min: 78, verdict: 'healthy', label: 'Healthy' },
  { min: 62, verdict: 'fair', label: 'Fair' },
  { min: 45, verdict: 'at-risk', label: 'At risk' },
  { min: 0, verdict: 'critical', label: 'Critical' },
];

export function verdictFor(score: number): HealthVerdict {
  return bandFor(score).verdict;
}

export function verdictLabel(score: number): string {
  return bandFor(score).label;
}

function bandFor(score: number): HealthBand {
  const clamped = clamp(score, 0, 100);
  // BANDS is ordered high-to-low, so the first match is the tightest band.
  return BANDS.find((band) => clamped >= band.min) ?? BANDS[BANDS.length - 1];
}

/**
 * Continuous colour along the health gradient. Used where a hard band edge
 * would misrepresent the data — the DNA strand and the hotspot marks read
 * as a smooth ramp rather than five discrete buckets.
 */
export function healthRamp(score: number): string {
  const stops: readonly (readonly [number, readonly [number, number, number]])[] = [
    [0, [242, 87, 108]], // critical
    [45, [242, 128, 79]], // poor
    [62, [233, 180, 76]], // fair
    [78, [62, 207, 178]], // good
    [100, [62, 207, 142]], // excellent
  ];
  const value = clamp(score, 0, 100);

  for (let i = 0; i < stops.length - 1; i++) {
    const [lowStop, lowRgb] = stops[i];
    const [highStop, highRgb] = stops[i + 1];
    if (value <= highStop) {
      const t = (value - lowStop) / (highStop - lowStop);
      const [r, g, b] = lowRgb.map((c, idx) =>
        Math.round(c + (highRgb[idx] - c) * t),
      );
      return `rgb(${r} ${g} ${b})`;
    }
  }
  return `rgb(62 207 142)`;
}

const RISK_ORDER: readonly RiskLevel[] = ['low', 'medium', 'high', 'critical'];

export function riskRank(risk: RiskLevel): number {
  return RISK_ORDER.indexOf(risk);
}

export function riskColorVar(risk: RiskLevel): string {
  switch (risk) {
    case 'critical':
      return 'var(--health-critical)';
    case 'high':
      return 'var(--health-poor)';
    case 'medium':
      return 'var(--health-fair)';
    case 'low':
      return 'var(--health-good)';
  }
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/** Maps a value from one numeric range into another. */
export function scale(
  value: number,
  fromMin: number,
  fromMax: number,
  toMin: number,
  toMax: number,
): number {
  if (fromMax === fromMin) return toMin;
  const t = (value - fromMin) / (fromMax - fromMin);
  return toMin + t * (toMax - toMin);
}
