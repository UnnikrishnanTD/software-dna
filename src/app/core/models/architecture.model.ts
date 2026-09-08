/** What a node in the architecture graph represents. */
export type ArchitectureNodeKind =
  | 'app'
  | 'module'
  | 'component'
  | 'service'
  | 'store'
  | 'api'
  | 'datastore'
  | 'external';

/** Which horizontal band the node sits in. Drives the layered layout. */
export type ArchitectureLayer =
  | 'presentation'
  | 'application'
  | 'domain'
  | 'infrastructure'
  | 'external';

export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';
export type ComplexityBand = 'low' | 'medium' | 'high' | 'very-high';

export interface ArchitectureNode {
  readonly id: string;
  readonly name: string;
  readonly kind: ArchitectureNodeKind;
  readonly layer: ArchitectureLayer;
  /** Source path, used to cross-link into the codebase explorer. */
  readonly path: string;
  readonly language: string;
  readonly complexity: ComplexityBand;
  /** Cyclomatic complexity, averaged across the unit. */
  readonly complexityScore: number;
  readonly linesOfCode: number;
  /** Coverage percentage, or null when no report was available. */
  readonly coverage: number | null;
  readonly risk: RiskLevel;
  /** Outgoing dependency count (fan-out). */
  readonly fanOut: number;
  /** Incoming dependency count (fan-in). */
  readonly fanIn: number;
  readonly description: string;
}

export type ArchitectureEdgeKind = 'imports' | 'calls' | 'implements' | 'reads';

export interface ArchitectureEdge {
  readonly id: string;
  readonly source: string;
  readonly target: string;
  readonly kind: ArchitectureEdgeKind;
  /** Relative strength of the coupling, 1–10. Drives stroke weight. */
  readonly weight: number;
}

export interface ArchitectureGraph {
  readonly nodes: readonly ArchitectureNode[];
  readonly edges: readonly ArchitectureEdge[];
  /** Cycles detected between modules, rendered as warnings. */
  readonly circularDependencies: readonly string[][];
}
