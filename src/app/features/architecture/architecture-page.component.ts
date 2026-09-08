import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

import {
  ArchitectureLayer,
  ArchitectureNode,
} from '../../core/models/architecture.model';
import { AnalysisStore } from '../../core/services/analysis-store';
import { clamp, riskColorVar } from '../../core/util/health';
import { IconComponent } from '../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';
import { NodeInspectorComponent } from './node-inspector.component';
import {
  GraphLayout,
  layoutGraph,
  NODE_HEIGHT,
  NODE_WIDTH,
} from './graph-layout';

interface Viewport {
  readonly x: number;
  readonly y: number;
  readonly scale: number;
}

const MIN_SCALE = 0.35;
const MAX_SCALE = 2.4;

const LAYER_FILTERS: readonly { key: ArchitectureLayer; label: string }[] = [
  { key: 'presentation', label: 'Presentation' },
  { key: 'application', label: 'Application' },
  { key: 'domain', label: 'Domain' },
  { key: 'infrastructure', label: 'Infrastructure' },
  { key: 'external', label: 'External' },
];

@Component({
  selector: 'sdna-architecture',
  standalone: true,
  imports: [
    RouterLink,
    IconComponent,
    PageHeaderComponent,
    StateViewComponent,
    NodeInspectorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './architecture-page.component.html',
  styleUrl: './architecture-page.component.scss',
})
export class ArchitecturePageComponent {
  private readonly store = inject(AnalysisStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly surface =
    viewChild.required<ElementRef<HTMLDivElement>>('surface');

  protected readonly NODE_WIDTH = NODE_WIDTH;
  protected readonly NODE_HEIGHT = NODE_HEIGHT;
  protected readonly layerFilters = LAYER_FILTERS;

  protected readonly analysis = this.store.analysis;

  protected readonly search = signal('');
  protected readonly hiddenLayers = signal<ReadonlySet<ArchitectureLayer>>(
    new Set(),
  );
  protected readonly hoveredId = signal<string | null>(null);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly viewport = signal<Viewport>({ x: 0, y: 0, scale: 1 });

  private panOrigin: { x: number; y: number; vx: number; vy: number } | null =
    null;

  protected readonly layout = computed<GraphLayout | null>(() => {
    const graph = this.analysis()?.architecture;
    return graph ? layoutGraph(graph) : null;
  });

  /** Nodes matching the search term, used to dim everything else. */
  protected readonly matchedIds = computed<ReadonlySet<string>>(() => {
    const term = this.search().trim().toLowerCase();
    if (term === '') return new Set();
    const nodes = this.analysis()?.architecture.nodes ?? [];
    return new Set(
      nodes
        .filter(
          (node) =>
            node.name.toLowerCase().includes(term) ||
            node.path.toLowerCase().includes(term) ||
            node.kind.toLowerCase().includes(term),
        )
        .map((node) => node.id),
    );
  });

  protected readonly matchCount = computed(() => this.matchedIds().size);

  /** The node whose relationships are currently highlighted. */
  private readonly focusId = computed(
    () => this.hoveredId() ?? this.selectedId(),
  );

  /** Ids directly connected to the focused node, in either direction. */
  protected readonly relatedIds = computed<ReadonlySet<string>>(() => {
    const id = this.focusId();
    const edges = this.analysis()?.architecture.edges ?? [];
    if (!id) return new Set();
    const related = new Set<string>([id]);
    for (const edge of edges) {
      if (edge.source === id) related.add(edge.target);
      if (edge.target === id) related.add(edge.source);
    }
    return related;
  });

  protected readonly selectedNode = computed<ArchitectureNode | null>(() => {
    const id = this.selectedId();
    if (!id) return null;
    return (
      this.analysis()?.architecture.nodes.find((node) => node.id === id) ?? null
    );
  });

  /** Everything the inspector needs about the selected node's neighbours. */
  protected readonly selectedRelations = computed(() => {
    const node = this.selectedNode();
    const graph = this.analysis()?.architecture;
    if (!node || !graph) return { dependsOn: [], usedBy: [] };

    const byId = new Map(graph.nodes.map((n) => [n.id, n]));
    return {
      dependsOn: graph.edges
        .filter((edge) => edge.source === node.id)
        .map((edge) => byId.get(edge.target))
        .filter((n): n is ArchitectureNode => Boolean(n)),
      usedBy: graph.edges
        .filter((edge) => edge.target === node.id)
        .map((edge) => byId.get(edge.source))
        .filter((n): n is ArchitectureNode => Boolean(n)),
    };
  });

  protected readonly cycleCount = computed(
    () => this.analysis()?.architecture.circularDependencies.length ?? 0,
  );

  protected readonly transform = computed(() => {
    const { x, y, scale } = this.viewport();
    return `translate(${x} ${y}) scale(${scale})`;
  });

  protected readonly zoomLabel = computed(
    () => `${Math.round(this.viewport().scale * 100)}%`,
  );

  constructor() {
    afterNextRender(() => this.fitInitialView());

    // A node id in the query string deep-links from the overview, the
    // hotspot map and the Doctor's answers.
    this.route.queryParamMap
      .pipe(
        map((params) => params.get('node')),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((id) => this.selectedId.set(id));
  }

  /**
   * On a narrow viewport, fitting all five layers across the screen makes
   * every node label unreadable. Better to open at a legible scale and let
   * the reader pan — the graph is built for panning anyway.
   */
  private fitInitialView(): void {
    const element = this.surface().nativeElement;
    const graph = this.layout();
    if (!graph || element.clientWidth === 0) return;

    const fittedScale = element.clientWidth / graph.width;
    if (fittedScale >= 0.55) return;

    const scale = clamp(0.55 / fittedScale, 1, MAX_SCALE);
    // Anchor to the left edge so the entry points of the graph — the
    // presentation layer — are what the reader sees first.
    this.viewport.set({ x: 0, y: 0, scale });
  }

  protected isVisible(node: ArchitectureNode): boolean {
    return !this.hiddenLayers().has(node.layer);
  }

  protected toggleLayer(layer: ArchitectureLayer): void {
    this.hiddenLayers.update((current) => {
      const next = new Set(current);
      if (next.has(layer)) next.delete(layer);
      else next.add(layer);
      return next;
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected select(id: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { node: id },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected riskColor(node: ArchitectureNode): string {
    return riskColorVar(node.risk);
  }

  /** Node opacity, folding together search and relationship highlighting. */
  protected nodeOpacity(node: ArchitectureNode): number {
    if (!this.isVisible(node)) return 0;
    if (this.matchCount() > 0 && !this.matchedIds().has(node.id)) return 0.16;
    if (this.focusId() && !this.relatedIds().has(node.id)) return 0.2;
    return 1;
  }

  protected edgeOpacity(source: string, target: string): number {
    const nodes = this.analysis()?.architecture.nodes ?? [];
    const sourceNode = nodes.find((n) => n.id === source);
    const targetNode = nodes.find((n) => n.id === target);
    if (
      (sourceNode && !this.isVisible(sourceNode)) ||
      (targetNode && !this.isVisible(targetNode))
    ) {
      return 0;
    }

    const focus = this.focusId();
    if (!focus) return this.matchCount() > 0 ? 0.08 : 0.22;
    return source === focus || target === focus ? 0.75 : 0.05;
  }

  // ---- Viewport controls -------------------------------------------------

  protected zoomBy(factor: number): void {
    this.viewport.update((view) => {
      const scale = clamp(view.scale * factor, MIN_SCALE, MAX_SCALE);
      const element = this.surface().nativeElement;
      const cx = element.clientWidth / 2;
      const cy = element.clientHeight / 2;
      // Keep the centre of the viewport fixed while scaling.
      return {
        scale,
        x: cx - ((cx - view.x) / view.scale) * scale,
        y: cy - ((cy - view.y) / view.scale) * scale,
      };
    });
  }

  protected resetView(): void {
    this.viewport.set({ x: 0, y: 0, scale: 1 });
    this.search.set('');
    this.hiddenLayers.set(new Set());
    this.select(null);
  }

  protected onWheel(event: WheelEvent): void {
    if (!event.ctrlKey && !event.metaKey) return;
    event.preventDefault();
    this.zoomBy(event.deltaY < 0 ? 1.12 : 0.89);
  }

  protected onPointerDown(event: PointerEvent): void {
    if (event.button !== 0) return;
    const view = this.viewport();
    this.panOrigin = { x: event.clientX, y: event.clientY, vx: view.x, vy: view.y };
    (event.target as Element).setPointerCapture?.(event.pointerId);
  }

  protected onPointerMove(event: PointerEvent): void {
    const origin = this.panOrigin;
    if (!origin) return;
    this.viewport.update((view) => ({
      ...view,
      x: origin.vx + (event.clientX - origin.x),
      y: origin.vy + (event.clientY - origin.y),
    }));
  }

  protected onPointerUp(): void {
    this.panOrigin = null;
  }
}
