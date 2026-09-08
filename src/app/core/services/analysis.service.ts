import { Observable } from 'rxjs';
import {
  AnalysisSummary,
  ComparisonResult,
  DoctorMessage,
  RepositoryAnalysis,
} from '../models';
import { AnalysisRun } from '../models/analysis-run.model';

/**
 * The contract every data source must satisfy.
 *
 * Declared as an abstract class rather than an interface so it doubles as
 * an Angular DI token — components inject `AnalysisService` and are wired
 * to `MockAnalysisService` today and `HttpAnalysisService` later, with no
 * component change.
 */
export abstract class AnalysisService {
  /** Every analysis available to the current user. */
  abstract listAnalyses(): Observable<readonly AnalysisSummary[]>;

  /** Full profile for one analysis. Errors if the id is unknown. */
  abstract getAnalysis(id: string): Observable<RepositoryAnalysis>;

  /**
   * Starts a scan and emits a snapshot each time a stage changes state.
   * Completes once the run finishes; the final snapshot carries the id to
   * navigate to.
   */
  abstract startAnalysis(repositoryUrl: string): Observable<AnalysisRun>;

  /** Side-by-side comparison of two completed analyses. */
  abstract compare(leftId: string, rightId: string): Observable<ComparisonResult>;

  /** Answers a question about one analysis. */
  abstract askDoctor(
    analysisId: string,
    question: string,
  ): Observable<DoctorMessage>;
}
