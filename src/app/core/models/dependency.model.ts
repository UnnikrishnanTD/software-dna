import { RiskLevel } from './architecture.model';

export type DependencyEcosystem = 'npm' | 'maven' | 'docker' | 'system';

export type DependencyStatus =
  | 'current'
  | 'minor-behind'
  | 'major-behind'
  | 'deprecated';

export interface Dependency {
  readonly id: string;
  readonly name: string;
  readonly ecosystem: DependencyEcosystem;
  readonly version: string;
  /** Null when no package registry was consulted. */
  readonly latestVersion: string | null;
  readonly status: DependencyStatus;
  readonly license: string;
  /** Whether the project depends on this directly or transitively. */
  readonly direct: boolean;
  /** Number of internal modules importing it. Drives concentration view. */
  readonly usedBy: number;
  readonly sizeKb: number | null;
  readonly risk: RiskLevel;
  /**
   * Advisories against the pinned version, or null when no vulnerability
   * database was consulted. Null and 0 are different claims.
   */
  readonly advisories: number | null;
  readonly note?: string;
}

/** A technology in the stack, aggregated across its dependencies. */
export interface Technology {
  readonly id: string;
  readonly name: string;
  readonly category:
    | 'frontend'
    | 'backend'
    | 'language'
    | 'datastore'
    | 'infrastructure'
    | 'tooling';
  /** Share of the codebase attributed to this technology, 0–100. */
  readonly share: number;
  readonly linesOfCode: number;
  readonly version: string;
  readonly dependencyCount: number;
}

export interface DependencyProfile {
  readonly total: number;
  readonly direct: number;
  /** Null: resolving the transitive tree requires a registry lookup. */
  readonly transitive: number | null;
  readonly outdated: number | null;
  readonly deprecated: number;
  readonly advisories: number | null;
  readonly dependencies: readonly Dependency[];
  readonly technologies: readonly Technology[];
}
