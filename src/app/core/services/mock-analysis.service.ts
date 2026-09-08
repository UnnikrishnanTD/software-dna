import { Injectable } from '@angular/core';
import { concat, defer, delay, Observable, of, scan, throwError, timer } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';

import {
  AnalysisSummary,
  ComparisonResult,
  ComparisonRow,
  DoctorMessage,
  RepositoryAnalysis,
} from '../models';
import {
  AnalysisRun,
  AnalysisStage,
  AnalysisStageId,
} from '../models/analysis-run.model';
import { AnalysisService } from './analysis.service';
import { ATLAS_ANALYSIS } from '../mock/atlas';
import { NOVA_ANALYSIS } from '../mock/nova';
import { buildDoctorMessage } from '../mock/doctor-responses';
import { verdictLabel } from '../util/health';
import { compareMeasurements, forLayout } from '../util/measurement';
import {
  isSameRepository,
  parseRepositoryRef,
} from '../util/repository-ref';

/** Every analysis the mock backend knows about, keyed by id. */
const ANALYSES: ReadonlyMap<string, RepositoryAnalysis> = new Map([
  [ATLAS_ANALYSIS.id, ATLAS_ANALYSIS],
  [NOVA_ANALYSIS.id, NOVA_ANALYSIS],
]);

/** The default subject of the demo experience. */
export const DEMO_ANALYSIS_ID = ATLAS_ANALYSIS.id;

interface StageScript {
  readonly id: AnalysisStageId;
  readonly label: string;
  readonly detail: string;
  readonly result: string;
  /** How long the stage appears to run, in milliseconds. */
  readonly durationMs: number;
}

/**
 * Builds the scan sequence from the analysis actually being run.
 *
 * Every figure reported here is read out of that analysis rather than
 * written down, so the scan can never claim to have found something the
 * resulting profile does not contain.
 *
 * Durations are deliberately uneven. A scan that ticks along at a constant
 * rate reads as a fake progress bar; real work is lumpy, and the varied
 * timings are what make the sequence feel like it is doing something.
 */
function buildStageScript(
  analysis: RepositoryAnalysis,
): readonly StageScript[] {
  const { stats, repository, architecture, dependencies, dna } = analysis;
  const years =
    analysis.evolution.points.length > 0
      ? analysis.evolution.points.length
      : 1;
  const advisories = dependencies.advisories;
  const ecosystems = [
    ...new Set(dependencies.dependencies.map((d) => d.ecosystem)),
  ];

  return [
    {
      id: 'connect',
      label: 'Repository connected',
      detail: 'Resolving default branch',
      result: `${repository.defaultBranch} @ ${stats.commits.toLocaleString()} commits`,
      durationMs: 620,
    },
    {
      id: 'technologies',
      label: 'Detecting technologies',
      detail: 'Fingerprinting manifests and sources',
      result: `${dependencies.technologies.length} technologies`,
      durationMs: 880,
    },
    {
      id: 'architecture',
      label: 'Mapping architecture',
      detail: `Resolving imports across ${stats.files.toLocaleString()} files`,
      result: `${architecture.nodes.length} units · ${architecture.edges.length} relationships`,
      durationMs: 1480,
    },
    {
      id: 'dependencies',
      label: 'Analysing dependencies',
      detail: `Resolving ${ecosystems.join(', ')} trees`,
      result: `${dependencies.total} packages · ${advisories} ${
        advisories === 1 ? 'advisory' : 'advisories'
      }`,
      durationMs: 1020,
    },
    {
      id: 'complexity',
      label: 'Inspecting complexity',
      detail: 'Walking syntax trees',
      result: `Median complexity ${medianComplexity(analysis)}`,
      durationMs: 1240,
    },
    {
      id: 'tests',
      label: 'Examining test structure',
      detail: 'Correlating suites with sources',
      result: `${stats.testCoverage}% line coverage`,
      durationMs: 760,
    },
    {
      id: 'history',
      label: `Reading ${years} years of history`,
      detail: `Attributing ${stats.commits.toLocaleString()} commits`,
      result: `${stats.contributors} contributors · ${analysis.hotspots.length} hotspots`,
      durationMs: 1340,
    },
    {
      id: 'synthesis',
      label: 'Generating Software DNA',
      detail: `Synthesising ${dna.dimensions.length} dimensions`,
      result: `Health ${dna.overall} · ${verdictLabel(dna.overall)}`,
      durationMs: 1560,
    },
  ];
}

function medianComplexity(analysis: RepositoryAnalysis): number {
  const scores = analysis.architecture.nodes
    .map((node) => node.complexityScore)
    .sort((a, b) => a - b);
  if (scores.length === 0) return 0;
  const middle = Math.floor(scores.length / 2);
  return scores.length % 2 === 0
    ? Math.round((scores[middle - 1] + scores[middle]) / 2)
    : scores[middle];
}

/**
 * Matches a pasted reference against the repositories this build actually
 * holds. Anything else cannot be analysed without a backend, and the caller
 * is told so rather than being quietly handed a substitute.
 */
function resolveRequestedAnalysis(
  repositoryUrl: string,
): RepositoryAnalysis | null {
  const parsed = parseRepositoryRef(repositoryUrl);
  if (!parsed) return null;

  for (const analysis of ANALYSES.values()) {
    if (isSameRepository(analysis.repository, parsed)) return analysis;
  }
  return null;
}

@Injectable()
export class MockAnalysisService extends AnalysisService {
  listAnalyses(): Observable<readonly AnalysisSummary[]> {
    const summaries: readonly AnalysisSummary[] = [...ANALYSES.values()].map(
      toSummary,
    );
    return of(summaries).pipe(delay(120));
  }

  getAnalysis(id: string): Observable<RepositoryAnalysis> {
    return defer(() => {
      const analysis = ANALYSES.get(id);
      return analysis
        ? of(analysis).pipe(delay(180))
        : throwError(
            () => new Error(`No analysis found for repository "${id}".`),
          );
    });
  }

  /**
   * Emits a fresh snapshot each time a stage starts and each time one
   * finishes, so the UI drives off real state transitions rather than a
   * timer pretending to be progress.
   */
  startAnalysis(repositoryUrl: string): Observable<AnalysisRun> {
    // If the pasted reference is a repository this build actually holds,
    // analyse that one for real. Otherwise fall back to the sample — and
    // say so, rather than dressing the sample up as the requested repo.
    const requested = resolveRequestedAnalysis(repositoryUrl);
    const target = requested ?? ATLAS_ANALYSIS;
    const usingSampleData = requested === null;
    const script = buildStageScript(target);

    const transitions: Observable<StageTransition>[] = [];
    for (const stage of script) {
      transitions.push(of({ id: stage.id, phase: 'start' as const }));
      transitions.push(
        timer(stage.durationMs).pipe(
          map(() => ({ id: stage.id, phase: 'finish' as const })),
        ),
      );
    }

    const initial: AnalysisRun = {
      requestedUrl: repositoryUrl,
      displayName: target.repository.displayName,
      usingSampleData,
      status: 'running',
      progress: 0,
      stages: script.map((stage) => ({
        id: stage.id,
        label: stage.label,
        detail: stage.detail,
        status: 'pending' as const,
      })),
    };

    return concat(of<StageTransition | null>(null), ...transitions).pipe(
      scan(
        (run: AnalysisRun, transition: StageTransition | null) =>
          transition ? applyTransition(run, transition, script, target.id) : run,
        initial,
      ),
    );
  }

  compare(leftId: string, rightId: string): Observable<ComparisonResult> {
    return defer(() => {
      const left = ANALYSES.get(leftId);
      const right = ANALYSES.get(rightId);
      if (!left || !right) {
        return throwError(
          () => new Error('Both repositories must be analysed before comparing.'),
        );
      }
      return of(buildComparison(left, right)).pipe(delay(220));
    });
  }

  askDoctor(analysisId: string, question: string): Observable<DoctorMessage> {
    return this.getAnalysis(analysisId).pipe(
      // The pause stands in for model latency; it also gives the thinking
      // indicator long enough to register as deliberate rather than a flash.
      switchMap((analysis) =>
        of(buildDoctorMessage(analysis, question)).pipe(
          delay(700 + Math.random() * 500),
        ),
      ),
    );
  }
}

interface StageTransition {
  readonly id: AnalysisStageId;
  readonly phase: 'start' | 'finish';
}

function applyTransition(
  run: AnalysisRun,
  transition: StageTransition,
  script: readonly StageScript[],
  analysisId: string,
): AnalysisRun {
  const stages: AnalysisStage[] = run.stages.map((stage) => {
    if (stage.id !== transition.id) return stage;
    if (transition.phase === 'start') return { ...stage, status: 'running' };
    return {
      ...stage,
      status: 'complete',
      result: script.find((s) => s.id === stage.id)?.result,
    };
  });

  const completed = stages.filter((s) => s.status === 'complete').length;
  const done = completed === stages.length;

  return {
    ...run,
    stages,
    progress: completed / stages.length,
    status: done ? 'complete' : 'running',
    analysisId: done ? analysisId : undefined,
  };
}

function buildComparison(
  left: RepositoryAnalysis,
  right: RepositoryAnalysis,
): ComparisonResult {
  const rows: ComparisonRow[] = left.dna.dimensions.map((dimension) => {
    const counterpart = right.dna.dimensions.find((d) => d.key === dimension.key);
    const rightScore = counterpart?.score ?? null;
    // Null when either side lacks the measurement: a dimension one repository
    // could not measure is not a loss for it.
    const delta =
      dimension.score === null || rightScore === null
        ? null
        : dimension.score - rightScore;
    return {
      key: dimension.key,
      label: dimension.label,
      left: dimension.score,
      right: rightScore,
      delta,
      leader: delta === null || delta === 0 ? 'tie' : delta > 0 ? 'left' : 'right',
    };
  });

  const leftWins = rows.filter((r) => r.leader === 'left');
  const rightWins = rows.filter((r) => r.leader === 'right');
  const biggestLeftGap = [...leftWins].sort((a, b) =>
    compareMeasurements(a.delta, b.delta, 'desc'),
  )[0];
  const biggestRightGap = [...rightWins].sort((a, b) =>
    compareMeasurements(a.delta, b.delta, 'asc'),
  )[0];

  return {
    left: toSummary(left),
    right: toSummary(right),
    rows,
    verdict: {
      headline:
        left.dna.overall === right.dna.overall
          ? 'Evenly matched overall, but strong in different places.'
          : `${(left.dna.overall > right.dna.overall ? left : right).repository.displayName} leads overall by ${Math.abs(left.dna.overall - right.dna.overall)} points.`,
      body: `${left.repository.displayName} wins ${leftWins.length} of ${rows.length} dimensions, ${right.repository.displayName} wins ${rightWins.length}. The gap is not uniform: these two systems have made opposite trade-offs. ${left.repository.displayName} has invested in structure and dependency hygiene, ${right.repository.displayName} in verification and security. Each one's weakness is the other's strength, which makes them unusually useful to read side by side.`,
      leftTakeaway: biggestLeftGap
        ? `Strongest advantage: ${biggestLeftGap.label}, ahead by ${forLayout(biggestLeftGap.delta)} points. Weakest relative to ${right.repository.displayName}: ${biggestRightGap?.label ?? '—'}.`
        : `No dimension leads ${right.repository.displayName}.`,
      rightTakeaway: biggestRightGap
        ? `Strongest advantage: ${biggestRightGap.label}, ahead by ${Math.abs(forLayout(biggestRightGap.delta))} points. Weakest relative to ${left.repository.displayName}: ${biggestLeftGap?.label ?? '—'}.`
        : `No dimension leads ${left.repository.displayName}.`,
    },
  };
}

function toSummary(analysis: RepositoryAnalysis): AnalysisSummary {
  return {
    id: analysis.id,
    displayName: analysis.repository.displayName,
    owner: analysis.repository.owner,
    name: analysis.repository.name,
    url: analysis.repository.url,
    overall: analysis.dna.overall,
    primaryLanguage: analysis.repository.primaryLanguage,
    generatedAt: analysis.generatedAt,
  };
}
