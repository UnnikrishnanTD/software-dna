import { ComplexityBand, RiskLevel } from './architecture.model';

/** Metrics attached to a leaf file in the codebase tree. */
export interface FileMetrics {
  readonly linesOfCode: number;
  readonly complexity: ComplexityBand;
  readonly complexityScore: number;
  readonly dependencies: number;
  readonly dependents: number;
  /** Commits touching this file in the analysed history. */
  readonly changes: number;
  /** Null unless a coverage report was found for this file. */
  readonly coverage: number | null;
  readonly risk: RiskLevel;
  readonly lastChanged: string;
  readonly primaryAuthor: string;
}

export interface FileNode {
  readonly id: string;
  readonly name: string;
  readonly path: string;
  readonly type: 'directory' | 'file';
  readonly language?: string;
  readonly children?: readonly FileNode[];
  readonly metrics?: FileMetrics;
  /** Rolled-up health for directories, 0–100. */
  readonly health?: number;
  /** Links back to the architecture graph when this file is a graph node. */
  readonly architectureNodeId?: string;
}
