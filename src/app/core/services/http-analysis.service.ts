import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import {
  catchError,
  map,
  switchMap,
  takeWhile,
  tap,
} from 'rxjs/operators';

import {
  AnalysisSummary,
  ComparisonResult,
  DoctorMessage,
  RepositoryAnalysis,
} from '../models';
import { AnalysisRun, AnalysisStage } from '../models/analysis-run.model';
import { AnalysisService } from './analysis.service';
import { environment } from '../../../environments/environment';

/**
 * The real data source.
 *
 * <p>Everything below is transport. The components inject the abstract
 * `AnalysisService` and cannot tell whether they are reading from this or from
 * `MockAnalysisService`, which is what makes the swap a one-line change in
 * `app.config.ts`.
 *
 * The only genuinely interesting piece is `startAnalysis`. The contract the
 * frontend was built against is a *stream* of run snapshots, but HTTP has no
 * stream: the backend returns an id and the client polls. That impedance
 * mismatch is resolved here and nowhere else — the analysis screen still
 * receives the same `Observable<AnalysisRun>` it always did. Replacing polling
 * with server-sent events later changes this file and nothing above it.
 */
@Injectable()
export class HttpAnalysisService extends AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  listAnalyses(): Observable<readonly AnalysisSummary[]> {
    return this.http
      .get<readonly AnalysisSummary[]>(`${this.baseUrl}/analyses`)
      .pipe(catchError((error) => this.fail(error, 'load the analyses')));
  }

  getAnalysis(id: string): Observable<RepositoryAnalysis> {
    return this.http
      .get<RepositoryAnalysis>(`${this.baseUrl}/analyses/${encodeURIComponent(id)}`)
      .pipe(catchError((error) => this.fail(error, 'load that analysis')));
  }

  /**
   * Starts a scan and emits a snapshot each time the server reports progress.
   *
   * Completes once the run reaches a terminal state, so the analysis screen's
   * subscription ends on its own rather than polling forever.
   */
  startAnalysis(repositoryUrl: string): Observable<AnalysisRun> {
    return this.http
      .post<StartResponse>(`${this.baseUrl}/analyses`, { repositoryUrl })
      .pipe(
        catchError((error) => this.fail(error, 'start the analysis')),
        switchMap((accepted) => this.pollUntilFinished(accepted, repositoryUrl)),
      );
  }

  compare(leftId: string, rightId: string): Observable<ComparisonResult> {
    const params = { left: leftId, right: rightId };
    return this.http
      .get<ComparisonResult>(`${this.baseUrl}/analyses/compare`, { params })
      .pipe(catchError((error) => this.fail(error, 'compare those analyses')));
  }

  askDoctor(analysisId: string, question: string): Observable<DoctorMessage> {
    return this.http
      .post<DoctorMessage>(`${this.baseUrl}/ai/questions`, { analysisId, question })
      .pipe(catchError((error) => this.fail(error, 'answer that question')));
  }

  // ---- Polling -----------------------------------------------------------

  private pollUntilFinished(
    accepted: StartResponse,
    requestedUrl: string,
  ): Observable<AnalysisRun> {
    const statusUrl =
      `${this.baseUrl}/analyses/${encodeURIComponent(accepted.analysisId)}/status`;

    let finished = false;

    return timer(0, environment.analysisPollIntervalMs).pipe(
      switchMap(() =>
        this.http.get<StatusResponse>(statusUrl).pipe(
          // A transient poll failure should not abandon a run that is still
          // going; report it and let the next tick try again.
          catchError(() => of(null)),
        ),
      ),
      map((status) =>
        status === null
          ? this.pendingRun(accepted, requestedUrl)
          : this.toRun(status, requestedUrl),
      ),
      tap((run) => {
        finished = run.status === 'complete' || run.status === 'failed';
      }),
      // Inclusive: the terminal snapshot is emitted, then the stream ends.
      takeWhile(() => !finished, true),
    );
  }

  private toRun(status: StatusResponse, requestedUrl: string): AnalysisRun {
    const stages: AnalysisStage[] = status.stages.map((stage) => ({
      id: stage.id,
      label: stage.label,
      detail: stage.detail,
      status: stage.status,
      result: stage.result ?? undefined,
    }));

    return {
      requestedUrl,
      displayName: status.displayName,
      // Nothing is ever substituted for the requested repository: the backend
      // either analyses it or fails.
      usingSampleData: false,
      status: this.toRunStatus(status.status),
      stages,
      progress: status.progress,
      analysisId: status.status === 'COMPLETED' ? status.analysisId : undefined,
      error: status.error ?? undefined,
    };
  }

  /** The first snapshot, before the server has reported anything. */
  private pendingRun(accepted: StartResponse, requestedUrl: string): AnalysisRun {
    return {
      requestedUrl,
      displayName: accepted.repository,
      usingSampleData: false,
      status: 'running',
      stages: [],
      progress: 0,
    };
  }

  private toRunStatus(status: BackendStatus): AnalysisRun['status'] {
    switch (status) {
      case 'COMPLETED':
        return 'complete';
      case 'FAILED':
      case 'CANCELLED':
        return 'failed';
      default:
        return 'running';
    }
  }

  // ---- Errors ------------------------------------------------------------

  /**
   * Turns a transport failure into the message the UI should show.
   *
   * The backend's structured error carries a human-readable message written
   * for exactly this purpose, so it is preferred over anything invented here.
   */
  private fail(error: unknown, action: string): Observable<never> {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as BackendError | null;
      if (body?.message) {
        return throwError(() => new Error(body.message));
      }
      if (error.status === 0) {
        return throwError(
          () =>
            new Error(
              `Could not reach the analysis service. Check that the backend ` +
                `is running at ${this.baseUrl}.`,
            ),
        );
      }
    }
    return throwError(() => new Error(`Could not ${action}.`));
  }
}

// ---- Wire shapes, kept local because nothing else needs them -------------

type BackendStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

interface StartResponse {
  readonly analysisId: string;
  readonly status: BackendStatus;
  readonly repository: string;
}

interface StatusResponse {
  readonly analysisId: string;
  readonly requestedUrl: string;
  readonly displayName: string;
  readonly status: BackendStatus;
  readonly progress: number;
  readonly message: string | null;
  readonly stages: readonly {
    readonly id: AnalysisStage['id'];
    readonly label: string;
    readonly detail: string;
    readonly status: AnalysisStage['status'];
    readonly result: string | null;
  }[];
  readonly errorCode: string | null;
  readonly error: string | null;
}

interface BackendError {
  readonly code: string;
  readonly message: string;
}
