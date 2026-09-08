import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

import { Hotspot } from '../../core/models/hotspot.model';
import { AnalysisStore } from '../../core/services/analysis-store';
import { healthRamp, riskColorVar, scale } from '../../core/util/health';
import { IconComponent } from '../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';

interface PlottedHotspot {
  readonly hotspot: Hotspot;
  readonly cx: number;
  readonly cy: number;
  readonly r: number;
  readonly color: string;
}

const PLOT_W = 800;
const PLOT_H = 460;
const PAD_L = 62;
const PAD_R = 24;
const PAD_T = 20;
const PAD_B = 46;

@Component({
  selector: 'sdna-hotspots',
  standalone: true,
  imports: [RouterLink, IconComponent, PageHeaderComponent, StateViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './hotspots-page.component.html',
  styleUrl: './hotspots-page.component.scss',
})
export class HotspotsPageComponent {
  private readonly store = inject(AnalysisStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly PLOT_W = PLOT_W;
  protected readonly PLOT_H = PLOT_H;
  protected readonly PAD_L = PAD_L;
  protected readonly PAD_T = PAD_T;

  protected readonly analysis = this.store.analysis;
  protected readonly hoveredId = signal<string | null>(null);
  protected readonly selectedId = signal<string | null>(null);

  protected readonly hotspots = computed<readonly Hotspot[]>(
    () => this.analysis()?.hotspots ?? [],
  );

  private readonly maxChanges = computed(() =>
    Math.max(...this.hotspots().map((h) => h.changes), 10),
  );

  private readonly maxComplexity = computed(() =>
    Math.max(...this.hotspots().map((h) => h.complexityScore), 10),
  );

  /**
   * The map answers one question: where do complexity and change frequency
   * meet? Change frequency runs along X, complexity up Y, so the dangerous
   * quadrant is always the top right and the eye goes there first.
   */
  protected readonly plotted = computed<readonly PlottedHotspot[]>(() =>
    this.hotspots().map((hotspot) => ({
      hotspot,
      cx: scale(hotspot.changes, 0, this.maxChanges(), PAD_L, PLOT_W - PAD_R),
      cy: scale(
        hotspot.complexityScore,
        0,
        this.maxComplexity(),
        PLOT_H - PAD_B,
        PAD_T,
      ),
      // Area, not radius, tracks risk so the marks compare honestly.
      r: 5 + Math.sqrt(hotspot.riskScore) * 1.35,
      color: healthRamp(100 - hotspot.riskScore),
    })),
  );

  /** The upper-right region where risk concentrates. */
  protected readonly dangerZone = computed(() => {
    const x = scale(this.maxChanges() * 0.55, 0, this.maxChanges(), PAD_L, PLOT_W - PAD_R);
    const y = PAD_T;
    return {
      x,
      y,
      width: PLOT_W - PAD_R - x,
      height:
        scale(this.maxComplexity() * 0.55, 0, this.maxComplexity(), PLOT_H - PAD_B, PAD_T) - y,
    };
  });

  protected readonly activeHotspot = computed<Hotspot | null>(() => {
    const id = this.hoveredId() ?? this.selectedId();
    return this.hotspots().find((h) => h.id === id) ?? null;
  });

  protected readonly selectedHotspot = computed<Hotspot | null>(() => {
    const id = this.selectedId();
    return this.hotspots().find((h) => h.id === id) ?? null;
  });

  protected readonly ranked = computed(() =>
    [...this.hotspots()].sort((a, b) => b.riskScore - a.riskScore),
  );

  protected readonly criticalCount = computed(
    () =>
      this.hotspots().filter(
        (h) => h.severity === 'critical' || h.severity === 'high',
      ).length,
  );

  /** Axis ticks, derived so they always span the actual data range. */
  protected readonly xTicks = computed(() =>
    [0, 0.25, 0.5, 0.75, 1].map((fraction) => ({
      value: Math.round(this.maxChanges() * fraction),
      x: scale(fraction, 0, 1, PAD_L, PLOT_W - PAD_R),
    })),
  );

  protected readonly yTicks = computed(() =>
    [0, 0.25, 0.5, 0.75, 1].map((fraction) => ({
      value: Math.round(this.maxComplexity() * fraction),
      y: scale(fraction, 0, 1, PLOT_H - PAD_B, PAD_T),
    })),
  );

  constructor() {
    this.route.queryParamMap
      .pipe(
        map((params) => params.get('hotspot')),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((id) => this.selectedId.set(id));
  }

  protected select(id: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { hotspot: id },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected severityColor(hotspot: Hotspot): string {
    return riskColorVar(hotspot.severity);
  }

  protected coverageColor(value: number): string {
    return healthRamp(value);
  }
}
