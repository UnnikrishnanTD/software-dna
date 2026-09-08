import { DnaDimensionKey } from './dna.model';

/** A single year in the repository's history. */
export interface EvolutionPoint {
  readonly year: number;
  /**
   * Dimension scores at the end of that year. Empty for historical years:
   * reconstructing them would mean re-analysing each year's tree.
   */
  readonly scores: Readonly<Partial<Record<DnaDimensionKey, number>>>;
  readonly overall: number | null;
  readonly contributors: number;
  readonly commits: number;
  readonly linesOfCode: number;
  readonly modules: number;
  readonly hotspots: number;
  readonly testCoverage: number | null;
}

export type MilestoneKind =
  | 'architecture'
  | 'technology'
  | 'scale'
  | 'quality'
  | 'incident';

/** A notable event on the evolution timeline. */
export interface EvolutionMilestone {
  readonly id: string;
  readonly year: number;
  /** 0–1 position within the year, so several events can share a year. */
  readonly offset: number;
  readonly kind: MilestoneKind;
  readonly title: string;
  readonly description: string;
  /** Net effect on the overall score, in points. */
  readonly impact: number;
}

export interface EvolutionHistory {
  readonly points: readonly EvolutionPoint[];
  readonly milestones: readonly EvolutionMilestone[];
}
