/** Identifiers for the stages of a repository scan, in execution order. */
export const ANALYSIS_STAGE_IDS = [
  'connect',
  'technologies',
  'architecture',
  'dependencies',
  'complexity',
  'tests',
  'history',
  'synthesis',
] as const;

export type AnalysisStageId = (typeof ANALYSIS_STAGE_IDS)[number];

export type AnalysisStageStatus =
  | 'pending'
  | 'running'
  | 'complete'
  | 'failed';

export interface AnalysisStage {
  readonly id: AnalysisStageId;
  readonly label: string;
  /** Shown while running, e.g. "1,284 files indexed". */
  readonly detail: string;
  readonly status: AnalysisStageStatus;
  /** Filled in as the stage completes, e.g. "8 technologies". */
  readonly result?: string;
}

export type AnalysisRunStatus =
  | 'idle'
  | 'running'
  | 'complete'
  | 'failed';

/** A snapshot of an in-flight scan, emitted as the run progresses. */
export interface AnalysisRun {
  /** Exactly what the user asked for. */
  readonly requestedUrl: string;
  /**
   * The repository actually being analysed. When no backend can reach the
   * requested repository this is a sample standing in for it, and
   * `usingSampleData` says so — the UI must never present one as the other.
   */
  readonly displayName: string;
  /** True when the requested repository could not be analysed for real. */
  readonly usingSampleData: boolean;
  readonly status: AnalysisRunStatus;
  readonly stages: readonly AnalysisStage[];
  /** 0–1 across the whole run. */
  readonly progress: number;
  /** Set once the run completes; the id to navigate to. */
  readonly analysisId?: string;
  readonly error?: string;
}
