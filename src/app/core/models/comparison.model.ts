import { DnaDimensionKey } from './dna.model';
import { AnalysisSummary } from './analysis.model';

/** One dimension compared across two repositories. */
export interface ComparisonRow {
  readonly key: DnaDimensionKey;
  readonly label: string;
  /** Null when the dimension was unavailable on that side. */
  readonly left: number | null;
  readonly right: number | null;
  /** left − right, or null when either side is unavailable. */
  readonly delta: number | null;
  readonly leader: 'left' | 'right' | 'tie';
}

export interface ComparisonVerdict {
  readonly headline: string;
  readonly body: string;
  readonly leftTakeaway: string;
  readonly rightTakeaway: string;
}

export interface ComparisonResult {
  readonly left: AnalysisSummary;
  readonly right: AnalysisSummary;
  readonly rows: readonly ComparisonRow[];
  readonly verdict: ComparisonVerdict;
}
