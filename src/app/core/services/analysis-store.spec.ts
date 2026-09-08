import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';

import { AnalysisSummary, ComparisonResult, DoctorMessage, RepositoryAnalysis } from '../models';
import { AnalysisRun } from '../models/analysis-run.model';
import { ATLAS_ANALYSIS } from '../mock/atlas';
import { AnalysisService } from './analysis.service';
import { AnalysisStore } from './analysis-store';

/** A stub that lets each test decide what the data source does. */
class StubAnalysisService extends AnalysisService {
  calls = 0;
  shouldFail = false;

  listAnalyses(): Observable<readonly AnalysisSummary[]> {
    return of([]);
  }

  getAnalysis(id: string): Observable<RepositoryAnalysis> {
    this.calls++;
    return this.shouldFail
      ? throwError(() => new Error(`No analysis found for "${id}".`)).pipe(delay(1))
      : of(ATLAS_ANALYSIS).pipe(delay(1));
  }

  startAnalysis(): Observable<AnalysisRun> {
    return of() as Observable<AnalysisRun>;
  }

  compare(): Observable<ComparisonResult> {
    return of() as Observable<ComparisonResult>;
  }

  askDoctor(): Observable<DoctorMessage> {
    return of() as Observable<DoctorMessage>;
  }
}

describe('AnalysisStore', () => {
  let store: AnalysisStore;
  let service: StubAnalysisService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AnalysisService, useClass: StubAnalysisService },
        AnalysisStore,
      ],
    });
    store = TestBed.inject(AnalysisStore);
    service = TestBed.inject(AnalysisService) as unknown as StubAnalysisService;
  });

  it('starts idle with nothing loaded', () => {
    expect(store.state()).toBe('idle');
    expect(store.analysis()).toBeNull();
    expect(store.isReady()).toBe(false);
  });

  it('moves through loading to ready', fakeAsync(() => {
    store.load('atlas-platform');
    expect(store.state()).toBe('loading');
    expect(store.isLoading()).toBe(true);

    tick(5);
    expect(store.state()).toBe('ready');
    expect(store.isReady()).toBe(true);
    expect(store.analysis()?.id).toBe('atlas-platform');
  }));

  it('ignores a repeat request for the id it already holds', fakeAsync(() => {
    store.load('atlas-platform');
    tick(5);
    store.load('atlas-platform');
    tick(5);

    expect(service.calls).toBe(1);
  }));

  it('loads again when the id changes', fakeAsync(() => {
    store.load('atlas-platform');
    tick(5);
    store.load('nova-platform');
    tick(5);

    expect(service.calls).toBe(2);
  }));

  it('surfaces a readable error and clears stale data', fakeAsync(() => {
    service.shouldFail = true;
    store.load('missing');
    tick(5);

    expect(store.state()).toBe('error');
    expect(store.hasError()).toBe(true);
    expect(store.analysis()).toBeNull();
    expect(store.error()).toContain('missing');
  }));

  it('retries the failed load and recovers', fakeAsync(() => {
    service.shouldFail = true;
    store.load('atlas-platform');
    tick(5);
    expect(store.hasError()).toBe(true);

    service.shouldFail = false;
    store.retry();
    tick(5);

    expect(store.isReady()).toBe(true);
    expect(store.analysis()?.id).toBe('atlas-platform');
  }));

  it('does nothing when retry is called before any load', () => {
    store.retry();
    expect(service.calls).toBe(0);
    expect(store.state()).toBe('idle');
  });
});
