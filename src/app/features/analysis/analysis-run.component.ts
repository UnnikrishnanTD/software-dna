import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AnalysisRun } from '../../core/models/analysis-run.model';
import { AnalysisService } from '../../core/services/analysis.service';
import { DnaCanvasComponent } from '../../shared/dna/dna-canvas.component';
import { IconComponent } from '../../shared/ui/icon.component';
import { NumeralComponent } from '../../shared/ui/numeral.component';
import { prefersReducedMotion } from '../../shared/util/motion';

/** How long the completion frame holds before the dashboard takes over. */
const HANDOFF_DELAY_MS = 1250;

@Component({
  selector: 'sdna-analysis-run',
  standalone: true,
  imports: [DnaCanvasComponent, IconComponent, NumeralComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analysis-run.component.html',
  styleUrl: './analysis-run.component.scss',
})
export class AnalysisRunComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(AnalysisService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly runSignal = signal<AnalysisRun | null>(null);
  private handoffTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly run = this.runSignal.asReadonly();

  protected readonly requestedUrl =
    this.route.snapshot.queryParamMap.get('repo') ??
    'github.com/atlas-commerce/atlas-platform';

  /** True once the run reports it fell back to the sample dataset. */
  protected readonly usingSampleData = computed(
    () => this.runSignal()?.usingSampleData ?? false,
  );

  protected readonly progressPercent = computed(() =>
    Math.round((this.runSignal()?.progress ?? 0) * 100),
  );

  protected readonly isComplete = computed(
    () => this.runSignal()?.status === 'complete',
  );

  protected readonly activeStageLabel = computed(() => {
    const stages = this.runSignal()?.stages ?? [];
    const running = stages.find((stage) => stage.status === 'running');
    return running?.detail ?? '';
  });

  constructor() {
    this.service
      .startAnalysis(this.requestedUrl)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (run) => {
          this.runSignal.set(run);
          if (run.status === 'complete' && run.analysisId) {
            this.scheduleHandoff(run.analysisId);
          }
        },
        error: () => void this.router.navigate(['/analyse']),
      });

    this.destroyRef.onDestroy(() => {
      if (this.handoffTimer) clearTimeout(this.handoffTimer);
    });
  }

  /** Lets the "DNA generated" frame land before navigating away. */
  private scheduleHandoff(analysisId: string): void {
    if (this.handoffTimer) return;
    const delay = prefersReducedMotion() ? 0 : HANDOFF_DELAY_MS;
    this.handoffTimer = setTimeout(() => {
      void this.router.navigate(['/analysis', analysisId]);
    }, delay);
  }

  protected skip(): void {
    const analysisId = this.runSignal()?.analysisId;
    void this.router.navigate(['/analysis', analysisId ?? 'atlas-platform']);
  }
}
