import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';

import {
  Dependency,
  DependencyEcosystem,
  DependencyStatus,
  Technology,
} from '../../core/models/dependency.model';
import { AnalysisStore } from '../../core/services/analysis-store';
import { riskColorVar, riskRank } from '../../core/util/health';
import { IconComponent } from '../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PanelComponent } from '../../shared/ui/panel.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';
import { NumeralComponent } from '../../shared/ui/numeral.component';

interface CompositionSegment {
  readonly technology: Technology;
  readonly widthPercent: number;
  readonly color: string;
}

interface EcosystemGroup {
  readonly ecosystem: DependencyEcosystem;
  readonly label: string;
  readonly dependencies: readonly Dependency[];
}

const ECOSYSTEM_LABELS: Readonly<Record<DependencyEcosystem, string>> = {
  npm: 'npm',
  maven: 'Maven',
  docker: 'Docker',
  system: 'System',
};

/**
 * Technology colours are assigned from the accent range rather than from
 * health, because a technology is not good or bad — the hue here is
 * identity, not judgement.
 */
const COMPOSITION_COLORS: readonly string[] = [
  '#5b8cff',
  '#3ecfb2',
  '#7ea6ff',
  '#5fe3c8',
  '#8f7dff',
  '#4fb8e8',
  '#6ee7c8',
  '#a4b8ff',
];

const STATUS_LABELS: Readonly<Record<DependencyStatus, string>> = {
  current: 'Current',
  'minor-behind': 'Minor update',
  'major-behind': 'Major behind',
  deprecated: 'Deprecated',
};

@Component({
  selector: 'sdna-dependencies',
  standalone: true,
  imports: [
    IconComponent,
    PageHeaderComponent,
    PanelComponent,
    StateViewComponent,
    NumeralComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dependencies-page.component.html',
  styleUrl: './dependencies-page.component.scss',
})
export class DependenciesPageComponent {
  private readonly store = inject(AnalysisStore);

  protected readonly analysis = this.store.analysis;
  protected readonly statusLabels = STATUS_LABELS;

  protected readonly selectedId = signal<string | null>(null);
  protected readonly hoveredTechnology = signal<string | null>(null);
  protected readonly statusFilter = signal<DependencyStatus | null>(null);

  protected readonly profile = computed(() => this.analysis()?.dependencies);

  /**
   * The stack answers "what is this system made of?" in one mark. Eight
   * separate bars would make the reader do the composition arithmetic
   * themselves; a single hundred-percent band does it for them.
   */
  protected readonly composition = computed<readonly CompositionSegment[]>(() => {
    const technologies = this.profile()?.technologies ?? [];
    const total = technologies.reduce((sum, tech) => sum + tech.share, 0) || 1;
    return technologies.map((technology, index) => ({
      technology,
      widthPercent: (technology.share / total) * 100,
      color: COMPOSITION_COLORS[index % COMPOSITION_COLORS.length],
    }));
  });

  protected readonly filtered = computed<readonly Dependency[]>(() => {
    const dependencies = this.profile()?.dependencies ?? [];
    const status = this.statusFilter();
    return status ? dependencies.filter((d) => d.status === status) : dependencies;
  });

  /**
   * Dependencies are grouped by ecosystem and laid out as a wall of chips
   * sized by how many internal modules import them. Concentration becomes
   * visible as a few large chips among many small ones — something a
   * sorted table cannot show at a glance.
   */
  protected readonly groups = computed<readonly EcosystemGroup[]>(() => {
    const byEcosystem = new Map<DependencyEcosystem, Dependency[]>();
    for (const dependency of this.filtered()) {
      const list = byEcosystem.get(dependency.ecosystem) ?? [];
      list.push(dependency);
      byEcosystem.set(dependency.ecosystem, list);
    }

    return [...byEcosystem.entries()]
      .map(([ecosystem, dependencies]) => ({
        ecosystem,
        label: ECOSYSTEM_LABELS[ecosystem],
        dependencies: [...dependencies].sort((a, b) => b.usedBy - a.usedBy),
      }))
      .sort((a, b) => b.dependencies.length - a.dependencies.length);
  });

  protected readonly selected = computed<Dependency | null>(() => {
    const id = this.selectedId();
    return this.profile()?.dependencies.find((d) => d.id === id) ?? null;
  });

  /** Advisories first, then by risk, then by how far behind the version is. */
  protected readonly needsAttention = computed(() =>
    (this.profile()?.dependencies ?? [])
      .filter((d) => (d.advisories ?? 0) > 0 || d.status !== 'current')
      .sort(
        (a, b) =>
          (b.advisories ?? 0) - (a.advisories ?? 0) ||
          riskRank(b.risk) - riskRank(a.risk) ||
          Number(b.status === 'major-behind') - Number(a.status === 'major-behind'),
      ),
  );

  protected readonly statusFilters = computed(() => {
    const dependencies = this.profile()?.dependencies ?? [];
    return (Object.keys(STATUS_LABELS) as DependencyStatus[])
      .map((status) => ({
        status,
        label: STATUS_LABELS[status],
        count: dependencies.filter((d) => d.status === status).length,
      }))
      .filter((entry) => entry.count > 0);
  });

  protected select(id: string | null): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  protected toggleStatus(status: DependencyStatus): void {
    this.statusFilter.update((current) => (current === status ? null : status));
  }

  /** Chip width tracks how widely a package is depended upon. */
  protected chipWidth(dependency: Dependency): number {
    return 96 + Math.min(dependency.usedBy, 46) * 3.4;
  }

  protected statusColor(status: DependencyStatus): string {
    switch (status) {
      case 'current':
        return 'var(--health-good)';
      case 'minor-behind':
        return 'var(--health-fair)';
      case 'major-behind':
        return 'var(--health-poor)';
      case 'deprecated':
        return 'var(--health-critical)';
    }
  }

  protected riskColor(dependency: Dependency): string {
    return riskColorVar(dependency.risk);
  }
}
