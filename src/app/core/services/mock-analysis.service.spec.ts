import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { AnalysisRun } from '../models/analysis-run.model';
import { AnalysisService } from './analysis.service';
import { MockAnalysisService } from './mock-analysis.service';
import { AnalysisSummary, RepositoryAnalysis, ComparisonResult } from '../models';

describe('MockAnalysisService', () => {
  let service: AnalysisService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: AnalysisService, useClass: MockAnalysisService }],
    });
    service = TestBed.inject(AnalysisService);
  });

  it('is reachable through the abstract AnalysisService token', () => {
    // This is the seam the HTTP implementation will later occupy, so it
    // matters that consumers never depend on the concrete class.
    expect(service).toBeInstanceOf(MockAnalysisService);
  });

  describe('getAnalysis', () => {
    it('returns a complete profile for a known repository', fakeAsync(() => {
      let result: RepositoryAnalysis | undefined;
      service.getAnalysis('atlas-platform').subscribe((value) => (result = value));
      tick(200);

      expect(result?.repository.displayName).toBe('Atlas Commerce Platform');
      expect(result?.dna.dimensions.length).toBe(8);
      expect(result?.architecture.nodes.length).toBeGreaterThan(0);
      expect(result?.hotspots.length).toBeGreaterThan(0);
      expect(result?.evolution.points.length).toBeGreaterThan(0);
    }));

    it('errors for an unknown repository rather than returning empty data', fakeAsync(() => {
      let error: Error | undefined;
      service.getAnalysis('does-not-exist').subscribe({
        error: (caught: Error) => (error = caught),
      });
      tick(200);

      expect(error).toBeDefined();
      expect(error?.message).toContain('does-not-exist');
    }));
  });

  describe('startAnalysis', () => {
    it('emits a snapshot for every stage start and finish', fakeAsync(() => {
      const runs: AnalysisRun[] = [];
      service
        .startAnalysis('github.com/atlas-commerce/atlas-platform')
        .subscribe((run) => runs.push(run));
      tick(20000);

      const first = runs[0];
      expect(first.status).toBe('running');
      expect(first.progress).toBe(0);
      expect(first.stages.every((stage) => stage.status === 'pending')).toBe(true);

      // One snapshot for the initial state, then two per stage.
      expect(runs.length).toBe(1 + first.stages.length * 2);
    }));

    it('advances progress monotonically and finishes complete', fakeAsync(() => {
      const runs: AnalysisRun[] = [];
      service.startAnalysis('github.com/owner/repo').subscribe((run) => runs.push(run));
      tick(20000);

      for (let i = 1; i < runs.length; i++) {
        expect(runs[i].progress).toBeGreaterThanOrEqual(runs[i - 1].progress);
      }

      const final = runs[runs.length - 1];
      expect(final.status).toBe('complete');
      expect(final.progress).toBe(1);
      expect(final.analysisId).toBe('atlas-platform');
      expect(final.stages.every((stage) => stage.status === 'complete')).toBe(true);
      expect(final.stages.every((stage) => Boolean(stage.result))).toBe(true);
    }));

    it('marks exactly one stage as running at a time', fakeAsync(() => {
      const runs: AnalysisRun[] = [];
      service.startAnalysis('github.com/owner/repo').subscribe((run) => runs.push(run));
      tick(20000);

      for (const run of runs) {
        const running = run.stages.filter((stage) => stage.status === 'running');
        expect(running.length).toBeLessThanOrEqual(1);
      }
    }));

    /**
     * The defect this guards: pasting any GitHub URL used to derive a
     * project name from it, report that name during the scan alongside
     * hard-coded figures, and then land on the Atlas dashboard — which read
     * as a broken analysis rather than an unimplemented one.
     */
    it('never presents a sample as the repository that was requested', fakeAsync(() => {
      const runs: AnalysisRun[] = [];
      service
        .startAnalysis('https://github.com/facebook/react')
        .subscribe((run) => runs.push(run));
      tick(20000);

      const final = runs[runs.length - 1];
      expect(final.usingSampleData).toBe(true);
      expect(final.requestedUrl).toBe('https://github.com/facebook/react');
      // The subject reported is the repository actually analysed.
      expect(final.displayName).toBe('Atlas Commerce Platform');
      expect(final.displayName).not.toContain('React');
      expect(final.analysisId).toBe('atlas-platform');
    }));

    it('analyses a repository this build really holds, in any URL form', fakeAsync(() => {
      for (const url of [
        'https://github.com/nova-retail/nova-platform',
        'git@github.com:nova-retail/nova-platform.git',
        'nova-retail/nova-platform',
        'NOVA-RETAIL/Nova-Platform',
      ]) {
        const runs: AnalysisRun[] = [];
        service.startAnalysis(url).subscribe((run) => runs.push(run));
        tick(20000);

        const final = runs[runs.length - 1];
        expect(final.usingSampleData)
          .withContext(url)
          .toBe(false);
        expect(final.displayName).withContext(url).toBe('Nova Platform');
        expect(final.analysisId).withContext(url).toBe('nova-platform');
      }
    }));

    it('reports figures taken from the repository it actually analysed', fakeAsync(() => {
      const runs: AnalysisRun[] = [];
      service
        .startAnalysis('nova-retail/nova-platform')
        .subscribe((run) => runs.push(run));
      tick(20000);

      const results = runs[runs.length - 1].stages.map((stage) => stage.result);
      // Nova's own numbers, not Atlas's.
      expect(results).toContain('main @ 11,860 commits');
      expect(results).toContain('17 units · 30 relationships');
      expect(results).toContain('84% line coverage');
      expect(results).toContain('Health 83 · Healthy');
      expect(results.join(' ')).not.toContain('21,470');
    }));

    it('reports Atlas figures when it falls back to the sample', fakeAsync(() => {
      const runs: AnalysisRun[] = [];
      service
        .startAnalysis('github.com/facebook/react')
        .subscribe((run) => runs.push(run));
      tick(20000);

      const results = runs[runs.length - 1].stages.map((stage) => stage.result);
      expect(results).toContain('main @ 21,470 commits');
      expect(results).toContain('44 units · 82 relationships');
      expect(results).toContain('Health 87 · Healthy');
    }));
  });

  describe('compare', () => {
    it('produces a row per dimension with the correct leader', fakeAsync(() => {
      let result: ComparisonResult | undefined;
      service
        .compare('atlas-platform', 'nova-platform')
        .subscribe((value) => (result = value));
      tick(300);

      expect(result?.rows.length).toBe(8);

      const architecture = result?.rows.find((row) => row.key === 'architecture');
      expect(architecture?.left).toBe(91);
      expect(architecture?.right).toBe(84);
      expect(architecture?.delta).toBe(7);
      expect(architecture?.leader).toBe('left');

      const testing = result?.rows.find((row) => row.key === 'testing');
      expect(testing?.leader).toBe('right');
    }));

    it('errors when either repository is unknown', fakeAsync(() => {
      let error: Error | undefined;
      service.compare('atlas-platform', 'missing').subscribe({
        error: (caught: Error) => (error = caught),
      });
      tick(300);
      expect(error).toBeDefined();
    }));
  });

  describe('askDoctor', () => {
    it('answers with structured blocks drawn from the analysis', fakeAsync(() => {
      let answer: { text: string; blocks?: readonly unknown[] } | undefined;
      service
        .askDoctor('atlas-platform', 'Which files are the biggest risk?')
        .subscribe((message) => (answer = message));
      tick(2000);

      expect(answer?.text).toContain('Atlas Commerce Platform');
      expect(answer?.blocks?.length).toBeGreaterThan(0);
    }));
  });

  it('lists every analysis available for comparison', fakeAsync(() => {
    let summaries: readonly AnalysisSummary[] = [];
    service.listAnalyses().subscribe((value) => (summaries = value));
    tick(200);

    expect(summaries.map((summary) => summary.id)).toEqual([
      'atlas-platform',
      'nova-platform',
    ]);
  }));

  it('publishes the repository identity needed to match a pasted URL', fakeAsync(() => {
    let summaries: readonly AnalysisSummary[] = [];
    service.listAnalyses().subscribe((value) => (summaries = value));
    tick(200);

    const atlas = summaries.find((summary) => summary.id === 'atlas-platform');
    expect(atlas?.owner).toBe('atlas-commerce');
    expect(atlas?.name).toBe('atlas-platform');
    expect(atlas?.url).toContain('github.com/atlas-commerce/atlas-platform');
  }));
});
