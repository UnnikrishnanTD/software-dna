import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { DnaDimension, DnaDimensionKey } from '../../core/models/dna.model';
import { AnalysisStore } from '../../core/services/analysis-store';
import { healthRamp, verdictLabel } from '../../core/util/health';
import { compareMeasurements, formatMeasurement } from '../../core/util/measurement';
import { DnaHelixComponent } from '../../shared/dna/dna-helix.component';
import { DeltaBadgeComponent } from '../../shared/ui/delta-badge.component';
import { DimensionMeterComponent } from '../../shared/ui/dimension-meter.component';
import { IconComponent } from '../../shared/ui/icon.component';
import { NumeralComponent } from '../../shared/ui/numeral.component';
import { PanelComponent } from '../../shared/ui/panel.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';
import { DimensionDetailComponent } from './dimension-detail.component';

@Component({
  selector: 'sdna-dna-overview',
  standalone: true,
  imports: [
    RouterLink,
    DnaHelixComponent,
    DimensionDetailComponent,
    DimensionMeterComponent,
    DeltaBadgeComponent,
    IconComponent,
    NumeralComponent,
    PanelComponent,
    StateViewComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dna-overview.component.html',
  styleUrl: './dna-overview.component.scss',
})
export class DnaOverviewComponent {
  private readonly store = inject(AnalysisStore);

  protected readonly analysis = this.store.analysis;

  /** The dimension under the pointer, shared between the helix and the list. */
  protected readonly hovered = signal<DnaDimensionKey | null>(null);
  /** The dimension pinned open in the inspector. */
  protected readonly selected = signal<DnaDimensionKey | null>(null);

  protected readonly dimensions = computed<readonly DnaDimension[]>(
    () => this.analysis()?.dna.dimensions ?? [],
  );

  protected readonly selectedDimension = computed(() => {
    const key = this.selected();
    return key ? (this.dimensions().find((d) => d.key === key) ?? null) : null;
  });

  protected readonly overall = computed(() => this.analysis()?.dna.overall ?? 0);
  protected readonly overallColor = computed(() => healthRamp(this.overall()));
  protected readonly overallVerdict = computed(() => verdictLabel(this.overall()));

  /** Sum of every dimension's delta — the direction of travel overall. */
  protected readonly overallDelta = computed(() => {
    const dimensions = this.dimensions();
    if (dimensions.length === 0) return 0;
    const weighted = dimensions.reduce(
      (sum, d) => sum + d.delta * d.weight,
      0,
    );
    return Math.round(weighted);
  });

  protected readonly strongest = computed(
    () =>
      [...this.dimensions()].sort((a, b) =>
        compareMeasurements(a.score, b.score, 'desc'),
      )[0] ?? null,
  );

  protected readonly weakest = computed(
    () =>
      [...this.dimensions()].sort((a, b) =>
        compareMeasurements(a.score, b.score, 'asc'),
      )[0] ?? null,
  );

  protected readonly topRisks = computed(() =>
    [...(this.analysis()?.hotspots ?? [])]
      .sort((a, b) => b.riskScore - a.riskScore)
      .slice(0, 3),
  );

  protected readonly headlineInsights = computed(() =>
    (this.analysis()?.insights ?? [])
      .filter((insight) => insight.kind === 'risk' || insight.kind === 'strength')
      .slice(0, 4),
  );

  /** Where the score stood previously, or null when it was never measured. */
  protected readonly previousScore = (dimension: DnaDimension): number | null =>
    dimension.score === null ? null : dimension.score - dimension.delta;

  protected readonly colorFor = (score: number | null): string =>
    score === null ? 'var(--text-muted)' : healthRamp(score);

  /** "—" when no coverage report exists, otherwise a percentage to 2 decimals. */
  protected readonly formatCoverage = (coverage: number | null): string =>
    formatMeasurement(coverage, { suffix: '%', decimals: 2 });

  protected select(key: DnaDimensionKey): void {
    this.selected.update((current) => (current === key ? null : key));
  }

  protected clearSelection(): void {
    this.selected.set(null);
  }
}
