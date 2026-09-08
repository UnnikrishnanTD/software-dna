/**
 * The eight dimensions that make up a Software DNA profile.
 * The order here is the canonical display order and the order in which
 * dimensions are laid out along the helix.
 */
export const DNA_DIMENSION_KEYS = [
  'architecture',
  'maintainability',
  'security',
  'performance',
  'testing',
  'dependencies',
  'documentation',
  'evolution',
] as const;

export type DnaDimensionKey = (typeof DNA_DIMENSION_KEYS)[number];

/** Coarse health banding, derived from a 0–100 score. */
export type HealthVerdict =
  | 'critical'
  | 'at-risk'
  | 'fair'
  | 'healthy'
  | 'exemplary';

/** A single measured dimension of the profile. */
export interface DnaDimension {
  readonly key: DnaDimensionKey;
  readonly label: string;
  /**
   * 0–100, or null when the dimension could not be assessed at all. The
   * backend never substitutes a number for a measurement it could not make.
   */
  readonly score: number | null;
  readonly verdict: HealthVerdict | null;
  /** One-line characterisation shown on hover. */
  readonly headline: string;
  /** Two or three sentences shown in the detail panel. */
  readonly summary: string;
  readonly strengths: readonly string[];
  readonly watchItems: readonly string[];
  /** Change versus the previous analysis, in points. */
  readonly delta: number;
  /** How much of the overall score this dimension accounts for (0–1). */
  readonly weight: number;
}

/** The complete DNA profile for one repository. */
export interface DnaScore {
  /** 0–100, the weighted roll-up of every dimension. */
  readonly overall: number;
  readonly verdict: HealthVerdict;
  readonly dimensions: readonly DnaDimension[];
  /**
   * Percentile against previously analysed repositories, or null while there
   * are too few of them for a percentile to carry information.
   */
  readonly percentile: number | null;
  /** 0–100. Falls when dimensions were scored from partial evidence. */
  readonly confidence: number;
}
