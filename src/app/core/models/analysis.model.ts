import { ArchitectureGraph } from './architecture.model';
import { FileNode } from './codebase.model';
import { DependencyProfile } from './dependency.model';
import { DnaScore } from './dna.model';
import { EvolutionHistory } from './evolution.model';
import { AiInsight, DoctorPrompt, RemediationStep } from './insight.model';
import { Hotspot } from './hotspot.model';

export interface RepositoryRef {
  readonly owner: string;
  readonly name: string;
  readonly displayName: string;
  readonly url: string;
  readonly defaultBranch: string;
  readonly description: string;
  readonly stars: number;
  readonly forks: number;
  readonly primaryLanguage: string;
  readonly createdAt: string;
  readonly lastCommitAt: string;
}

/** Headline counts shown across the shell and overview. */
export interface AnalysisStats {
  readonly files: number;
  readonly linesOfCode: number;
  readonly modules: number;
  readonly services: number;
  readonly components: number;
  readonly commits: number;
  readonly contributors: number;
  /** Null unless the repository committed a coverage report. */
  readonly testCoverage: number | null;
  readonly analysisDurationMs: number;
}

/** The complete result of analysing one repository. */
export interface RepositoryAnalysis {
  readonly id: string;
  readonly repository: RepositoryRef;
  readonly generatedAt: string;
  readonly stats: AnalysisStats;
  readonly dna: DnaScore;
  readonly architecture: ArchitectureGraph;
  readonly codebase: FileNode;
  readonly hotspots: readonly Hotspot[];
  readonly dependencies: DependencyProfile;
  readonly evolution: EvolutionHistory;
  readonly insights: readonly AiInsight[];
  readonly remediation: readonly RemediationStep[];
  readonly doctorPrompts: readonly DoctorPrompt[];
  /** Dimensions that could not be fully computed, driving partial states. */
  readonly incompleteDimensions: readonly string[];
}

/** Lightweight descriptor for lists and the comparison picker. */
export interface AnalysisSummary {
  readonly id: string;
  readonly displayName: string;
  readonly owner: string;
  /** Repository slug, so a pasted URL can be matched without a full fetch. */
  readonly name: string;
  readonly url: string;
  readonly overall: number;
  readonly primaryLanguage: string;
  readonly generatedAt: string;
}
