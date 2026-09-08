import { ComplexityBand, RiskLevel } from './architecture.model';

/**
 * A hotspot is the intersection of high complexity and high change
 * frequency — the places where technical risk actually concentrates.
 */
export interface Hotspot {
  readonly id: string;
  readonly name: string;
  readonly path: string;
  /** Commits in the analysed window. Drives the X axis. */
  readonly changes: number;
  /** Cyclomatic complexity. Drives the Y axis. */
  readonly complexityScore: number;
  readonly complexity: ComplexityBand;
  readonly dependencies: number;
  readonly bugFixes: number;
  readonly linesOfCode: number;
  /** Null unless a coverage report was found for this file. */
  readonly coverage: number | null;
  readonly contributors: number;
  /** Composite 0–100; drives mark size and severity banding. */
  readonly riskScore: number;
  readonly severity: RiskLevel;
  /** Plain-language explanation of why this file is flagged. */
  readonly rationale: string;
  readonly recommendation: string;
  readonly architectureNodeId?: string;
}
