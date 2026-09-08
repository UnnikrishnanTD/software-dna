import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';

import { DNA_DIMENSION_KEYS, DnaDimensionKey } from '../../core/models/dna.model';
import {
  EvolutionMilestone,
  EvolutionPoint,
  MilestoneKind,
} from '../../core/models/evolution.model';
import { AnalysisStore } from '../../core/services/analysis-store';
import { healthRamp, scale } from '../../core/util/health';
import { forLayout, formatMeasurement, isMeasured } from '../../core/util/measurement';
import { DeltaBadgeComponent } from '../../shared/ui/delta-badge.component';
import { DimensionMeterComponent } from '../../shared/ui/dimension-meter.component';
import { IconComponent } from '../../shared/ui/icon.component';
import { NumeralComponent } from '../../shared/ui/numeral.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PanelComponent } from '../../shared/ui/panel.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';

interface TimelineYear {
  readonly point: EvolutionPoint;
  readonly x: number;
  readonly y: number;
  /** False for years whose dimension scores were never recomputed. */
  readonly measured: boolean;
}

interface TimelineMilestone {
  readonly milestone: EvolutionMilestone;
  readonly x: number;
  readonly y: number;
  readonly color: string;
}

const TL_W = 900;
const TL_H = 200;
const TL_PAD_X = 48;
const TL_TOP = 34;
const TL_BOTTOM = 150;

const MILESTONE_COLORS: Readonly<Record<MilestoneKind, string>> = {
  architecture: 'var(--accent)',
  technology: 'var(--helix)',
  scale: 'var(--accent-bright)',
  quality: 'var(--success)',
  incident: 'var(--danger)',
};

const MILESTONE_LABELS: Readonly<Record<MilestoneKind, string>> = {
  architecture: 'Architecture',
  technology: 'Technology',
  scale: 'Scale',
  quality: 'Quality',
  incident: 'Incident',
};

@Component({
  selector: 'sdna-evolution',
  standalone: true,
  imports: [
    DeltaBadgeComponent,
    DimensionMeterComponent,
    IconComponent,
    NumeralComponent,
    PageHeaderComponent,
    PanelComponent,
    StateViewComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './evolution-page.component.html',
  styleUrl: './evolution-page.component.scss',
})
export class EvolutionPageComponent {
  private readonly store = inject(AnalysisStore);

  protected readonly TL_W = TL_W;
  protected readonly TL_H = TL_H;
  protected readonly TL_BOTTOM = TL_BOTTOM;
  protected readonly milestoneLabels = MILESTONE_LABELS;
  protected readonly dimensionKeys = DNA_DIMENSION_KEYS;

  protected readonly analysis = this.store.analysis;

  protected readonly points = computed<readonly EvolutionPoint[]>(
    () => this.analysis()?.evolution.points ?? [],
  );

  /**
   * The year the reader has picked, or null while they have picked none.
   * Deriving the selection this way means the timeline opens on the most
   * recent year — the state the rest of the application describes — without
   * needing a lifecycle hook to set an initial index after the data lands.
   */
  private readonly chosenIndex = signal<number | null>(null);

  protected readonly selectedIndex = computed(() => {
    const chosen = this.chosenIndex();
    const lastIndex = Math.max(this.points().length - 1, 0);
    return chosen === null ? lastIndex : Math.min(chosen, lastIndex);
  });

  protected readonly selectedPoint = computed<EvolutionPoint | null>(
    () => this.points()[this.selectedIndex()] ?? null,
  );

  protected readonly previousPoint = computed<EvolutionPoint | null>(() => {
    const index = this.selectedIndex();
    return index > 0 ? (this.points()[index - 1] ?? null) : null;
  });

  /** "—" when no coverage report exists, otherwise a percentage to 2 decimals. */
  protected readonly formatCoverage = (coverage: number | null): string =>
    formatMeasurement(coverage, { suffix: '%', decimals: 2 });

  protected readonly firstYear = computed(() => this.points()[0]?.year ?? 0);
  protected readonly lastYear = computed(
    () => this.points()[this.points().length - 1]?.year ?? 0,
  );

  private readonly xFor = (index: number): number => {
    const count = this.points().length;
    if (count <= 1) return TL_PAD_X;
    return scale(index, 0, count - 1, TL_PAD_X, TL_W - TL_PAD_X);
  };

  /** The overall-score trajectory, drawn as the timeline's spine. */
  protected readonly years = computed<readonly TimelineYear[]>(() =>
    this.points().map((point, index) => ({
      point,
      x: this.xFor(index),
      // A year with no recomputed score sits on the baseline; the marker is
      // drawn hollow so it is not mistaken for a measured low.
      y: scale(forLayout(point.overall, 40), 40, 100, TL_BOTTOM, TL_TOP),
      measured: isMeasured(point.overall),
    })),
  );

  protected readonly trajectoryPath = computed(() => {
    const years = this.years();
    if (years.length === 0) return '';
    // Catmull-Rom style smoothing keeps the trend readable without
    // implying data points that do not exist.
    return years
      .map((year, index) => {
        if (index === 0) return `M${year.x} ${year.y}`;
        const previous = years[index - 1];
        const midX = (previous.x + year.x) / 2;
        return `C${midX} ${previous.y}, ${midX} ${year.y}, ${year.x} ${year.y}`;
      })
      .join(' ');
  });

  protected readonly areaPath = computed(() => {
    const path = this.trajectoryPath();
    const years = this.years();
    if (path === '' || years.length === 0) return '';
    const last = years[years.length - 1];
    const first = years[0];
    return `${path} L${last.x} ${TL_BOTTOM} L${first.x} ${TL_BOTTOM} Z`;
  });

  protected readonly milestones = computed<readonly TimelineMilestone[]>(() => {
    const all = this.analysis()?.evolution.milestones ?? [];
    const points = this.points();
    return all.map((milestone) => {
      const index = points.findIndex((p) => p.year === milestone.year);
      const base = this.xFor(index < 0 ? 0 : index);
      const next = this.xFor(Math.min((index < 0 ? 0 : index) + 1, points.length - 1));
      return {
        milestone,
        x: base + (next - base) * milestone.offset,
        // Positive-impact events sit above the spine, negative below, so
        // the shape of the history is readable before any label is.
        y: milestone.impact >= 0 ? TL_TOP - 16 : TL_BOTTOM + 18,
        color: MILESTONE_COLORS[milestone.kind],
      };
    });
  });

  protected readonly selectedMilestones = computed<readonly EvolutionMilestone[]>(
    () => {
      const year = this.selectedPoint()?.year;
      if (year === undefined) return [];
      return (this.analysis()?.evolution.milestones ?? []).filter(
        (milestone) => milestone.year === year,
      );
    },
  );

  /** Dimension scores at the selected year, with year-over-year deltas. */
  protected readonly dimensionRows = computed(() => {
    const point = this.selectedPoint();
    const previous = this.previousPoint();
    const labels = this.analysis()?.dna.dimensions ?? [];
    if (!point) return [];

    return DNA_DIMENSION_KEYS.map((key: DnaDimensionKey) => {
      const score = point.scores[key] ?? null;
      const previousScore = previous?.scores[key] ?? null;
      return {
        key,
        label: labels.find((d) => d.key === key)?.label ?? key,
        score,
        // A delta needs both endpoints; without them it is zero, not a guess.
        delta:
          score !== null && previousScore !== null ? score - previousScore : 0,
        previous: previousScore,
      };
    });
  });

  /** Legend entries, built here so the template needs no type casts. */
  protected readonly milestoneLegend: readonly {
    kind: MilestoneKind;
    label: string;
    color: string;
  }[] = (
    ['architecture', 'technology', 'scale', 'quality', 'incident'] as const
  ).map((kind) => ({
    kind,
    label: MILESTONE_LABELS[kind],
    color: MILESTONE_COLORS[kind],
  }));

  protected readonly totalGrowth = computed(() => {
    const points = this.points();
    if (points.length < 2) return 0;
    const latest = points[points.length - 1].overall;
    const earliest = points[0].overall;
    // Growth is only meaningful when both ends were scored.
    return isMeasured(latest) && isMeasured(earliest) ? latest - earliest : 0;
  });

  protected onSlider(event: Event): void {
    this.chosenIndex.set(Number((event.target as HTMLInputElement).value));
  }

  protected selectYear(index: number): void {
    this.chosenIndex.set(index);
  }

  protected colorFor(score: number | null): string {
    return score === null ? 'var(--text-faint)' : healthRamp(score);
  }

  protected milestoneColor(kind: MilestoneKind): string {
    return MILESTONE_COLORS[kind];
  }

  /** Year-over-year change for a headline metric. */
  protected deltaOf(key: 'contributors' | 'commits' | 'linesOfCode' | 'modules' | 'hotspots' | 'testCoverage'): number {
    const point = this.selectedPoint();
    const previous = this.previousPoint();
    if (!point || !previous) return 0;
    const current = point[key];
    const before = previous[key];
    return isMeasured(current) && isMeasured(before) ? current - before : 0;
  }
}
