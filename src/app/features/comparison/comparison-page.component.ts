import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

import {
  AnalysisSummary,
  ComparisonResult,
  ComparisonRow,
} from '../../core/models';
import { AnalysisService } from '../../core/services/analysis.service';
import { AnalysisStore } from '../../core/services/analysis-store';
import { healthRamp } from '../../core/util/health';
import { IconComponent } from '../../shared/ui/icon.component';
import { NumeralComponent } from '../../shared/ui/numeral.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PanelComponent } from '../../shared/ui/panel.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';

interface DivergingRow {
  readonly row: ComparisonRow;
  /**
   * Each bar is split into the portion both repositories share and the
   * portion by which the leader is ahead. Drawing the shared part dim and
   * the lead bright makes the *gap* the prominent mark, without distorting
   * the 0–100 scale the way a zoomed axis would.
   */
  readonly sharedWidth: number;
  readonly leftLead: number;
  readonly rightLead: number;
  readonly leftColor: string;
  readonly rightColor: string;
}

@Component({
  selector: 'sdna-comparison',
  standalone: true,
  imports: [
    IconComponent,
    NumeralComponent,
    PageHeaderComponent,
    PanelComponent,
    StateViewComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './comparison-page.component.html',
  styleUrl: './comparison-page.component.scss',
})
export class ComparisonPageComponent {
  private readonly store = inject(AnalysisStore);
  private readonly service = inject(AnalysisService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly available = signal<readonly AnalysisSummary[]>([]);
  protected readonly result = signal<ComparisonResult | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly leftId = signal<string>('');
  protected readonly rightId = signal<string>('');

  constructor() {
    this.service
      .listAnalyses()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summaries) => {
          this.available.set(summaries);
          const currentId = this.store.analysis()?.id ?? summaries[0]?.id ?? '';
          const other =
            summaries.find((summary) => summary.id !== currentId)?.id ?? currentId;
          this.leftId.set(currentId);
          this.rightId.set(other);
          this.runComparison();
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('Could not load the list of analysed repositories.');
        },
      });
  }

  /**
   * Bars diverge from a central axis rather than sitting side by side.
   * The question a comparison answers is "who leads, and by how much" —
   * a shared centre line makes the answer readable without arithmetic.
   */
  protected readonly rows = computed<readonly DivergingRow[]>(() => {
    const result = this.result();
    if (!result) return [];
    return result.rows.map((row) => {
      // A dimension one side could not measure has no bars at all: drawing a
      // zero-length bar would read as a score of zero.
      if (row.left === null || row.right === null) {
        return {
          row,
          sharedWidth: 0,
          leftLead: 0,
          rightLead: 0,
          leftColor: 'var(--text-faint)',
          rightColor: 'var(--text-faint)',
        };
      }
      const shared = Math.min(row.left, row.right);
      return {
        row,
        sharedWidth: shared,
        leftLead: row.left - shared,
        rightLead: row.right - shared,
        leftColor: healthRamp(row.left),
        rightColor: healthRamp(row.right),
      };
    });
  });

  protected readonly leftWins = computed(
    () => this.result()?.rows.filter((row) => row.leader === 'left').length ?? 0,
  );

  protected readonly rightWins = computed(
    () => this.result()?.rows.filter((row) => row.leader === 'right').length ?? 0,
  );

  protected readonly canCompare = computed(
    () => this.available().length >= 2,
  );

  protected onSelectLeft(event: Event): void {
    this.leftId.set((event.target as HTMLSelectElement).value);
    this.runComparison();
  }

  protected onSelectRight(event: Event): void {
    this.rightId.set((event.target as HTMLSelectElement).value);
    this.runComparison();
  }

  protected swap(): void {
    const left = this.leftId();
    this.leftId.set(this.rightId());
    this.rightId.set(left);
    this.runComparison();
  }

  protected runComparison(): void {
    const left = this.leftId();
    const right = this.rightId();
    if (!left || !right) return;

    this.loading.set(true);
    this.errorMessage.set(null);

    of(null)
      .pipe(
        switchMap(() => this.service.compare(left, right)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.result.set(null);
          this.loading.set(false);
          this.errorMessage.set(
            error instanceof Error
              ? error.message
              : 'Could not compare these repositories.',
          );
        },
      });
  }

  protected colorFor(score: number): string {
    return healthRamp(score);
  }
}
