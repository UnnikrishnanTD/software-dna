import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';

import { RepositoryAnalysis } from '../models';
import { AnalysisService } from './analysis.service';

export type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Holds the analysis currently being explored.
 *
 * Provided on the analysis shell route rather than in root, so it is created
 * when the user enters an analysis and torn down when they leave. Child
 * routes read from it and never load data themselves.
 */
@Injectable()
export class AnalysisStore {
  private readonly service = inject(AnalysisService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly analysisSignal = signal<RepositoryAnalysis | null>(null);
  private readonly stateSignal = signal<LoadState>('idle');
  private readonly errorSignal = signal<string | null>(null);
  private requestedId: string | null = null;

  readonly analysis = this.analysisSignal.asReadonly();
  readonly state = this.stateSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  readonly isLoading = computed(() => this.stateSignal() === 'loading');
  readonly isReady = computed(() => this.stateSignal() === 'ready');
  readonly hasError = computed(() => this.stateSignal() === 'error');

  /** Dimensions the engine could not fully compute, for partial states. */
  readonly incomplete = computed(
    () => this.analysisSignal()?.incompleteDimensions ?? [],
  );

  /** Loads an analysis, ignoring repeat requests for the same id. */
  load(id: string): void {
    if (this.requestedId === id && this.stateSignal() !== 'error') return;
    this.requestedId = id;
    this.stateSignal.set('loading');
    this.errorSignal.set(null);

    this.service
      .getAnalysis(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (analysis) => {
          this.analysisSignal.set(analysis);
          this.stateSignal.set('ready');
        },
        error: (error: unknown) => {
          this.analysisSignal.set(null);
          this.errorSignal.set(
            error instanceof Error
              ? error.message
              : 'Something went wrong while analysing the repository.',
          );
          this.stateSignal.set('error');
        },
      });
  }

  /** Re-runs the most recent load. Used by the error state's retry action. */
  retry(): void {
    const id = this.requestedId;
    if (!id) return;
    this.requestedId = null;
    this.load(id);
  }
}
