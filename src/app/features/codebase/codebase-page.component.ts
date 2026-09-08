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

import { FileNode } from '../../core/models/codebase.model';
import { AnalysisStore } from '../../core/services/analysis-store';
import { healthRamp, riskColorVar } from '../../core/util/health';
import { IconComponent } from '../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';
import { DimensionMeterComponent } from '../../shared/ui/dimension-meter.component';

/** One rendered line of the tree. */
interface TreeRow {
  readonly node: FileNode;
  readonly depth: number;
  readonly expanded: boolean;
  readonly hasChildren: boolean;
}

@Component({
  selector: 'sdna-codebase',
  standalone: true,
  imports: [
    RouterLink,
    IconComponent,
    PageHeaderComponent,
    StateViewComponent,
    DimensionMeterComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './codebase-page.component.html',
  styleUrl: './codebase-page.component.scss',
})
export class CodebasePageComponent {
  private readonly store = inject(AnalysisStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly analysis = this.store.analysis;

  protected readonly search = signal('');
  protected readonly selectedPath = signal<string | null>(null);
  protected readonly expanded = signal<ReadonlySet<string>>(
    // Open the first two levels by default: enough to show the shape of the
    // repository without making the reader click to see anything at all.
    new Set(['', 'web', 'services']),
  );

  constructor() {
    this.route.queryParamMap
      .pipe(
        map((params) => params.get('path')),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((path) => {
        this.selectedPath.set(path);
        if (path) this.revealPath(path);
      });
  }

  /**
   * The visible tree, flattened into rows.
   *
   * Flattening rather than nesting recursive components keeps rendering to
   * a single `@for` over exactly the rows on screen, which is what lets the
   * tree stay responsive as the repository grows.
   */
  protected readonly rows = computed<readonly TreeRow[]>(() => {
    const root = this.analysis()?.codebase;
    if (!root) return [];

    const term = this.search().trim().toLowerCase();
    const expanded = this.expanded();
    const rows: TreeRow[] = [];

    const walk = (node: FileNode, depth: number): boolean => {
      const children = node.children ?? [];
      const selfMatches =
        term === '' ||
        node.name.toLowerCase().includes(term) ||
        node.path.toLowerCase().includes(term);

      if (node.type === 'file') {
        if (selfMatches) rows.push({ node, depth, expanded: false, hasChildren: false });
        return selfMatches;
      }

      // Directories are kept when they, or anything beneath them, match.
      const index = rows.length;
      rows.push({
        node,
        depth,
        expanded: term !== '' || expanded.has(node.path),
        hasChildren: children.length > 0,
      });

      let anyMatch = selfMatches;
      if (term !== '' || expanded.has(node.path)) {
        for (const child of children) {
          if (walk(child, depth + 1)) anyMatch = true;
        }
      } else {
        anyMatch =
          selfMatches || (term !== '' && subtreeMatches(node, term));
      }

      if (!anyMatch && term !== '') {
        rows.splice(index);
        return false;
      }
      return anyMatch;
    };

    for (const child of root.children ?? []) walk(child, 0);
    return rows;
  });

  protected readonly selectedNode = computed<FileNode | null>(() => {
    const path = this.selectedPath();
    const root = this.analysis()?.codebase;
    return path && root ? findByPath(root, path) : null;
  });

  /** Files ranked by health, so the explorer answers "what is worst?". */
  protected readonly weakestFiles = computed(() => {
    const root = this.analysis()?.codebase;
    if (!root) return [];
    return collectFiles(root)
      .sort((a, b) => (a.health ?? 100) - (b.health ?? 100))
      .slice(0, 5);
  });

  protected readonly matchCount = computed(
    () => this.rows().filter((row) => row.node.type === 'file').length,
  );

  protected toggle(node: FileNode): void {
    if (node.type !== 'directory') return;
    this.expanded.update((current) => {
      const next = new Set(current);
      if (next.has(node.path)) next.delete(node.path);
      else next.add(node.path);
      return next;
    });
  }

  protected activate(node: FileNode): void {
    if (node.type === 'directory') {
      this.toggle(node);
      return;
    }
    this.select(node.path);
  }

  protected select(path: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { path },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected healthColor(value: number | undefined): string {
    return healthRamp(value ?? 0);
  }

  protected riskColor(node: FileNode): string {
    return node.metrics ? riskColorVar(node.metrics.risk) : 'var(--text-muted)';
  }

  /** Expands every ancestor of a deep-linked path so it is visible. */
  private revealPath(path: string): void {
    const segments = path.split('/');
    const ancestors: string[] = [];
    for (let i = 1; i < segments.length; i++) {
      ancestors.push(segments.slice(0, i).join('/'));
    }
    this.expanded.update((current) => new Set([...current, ...ancestors]));
  }
}

function subtreeMatches(node: FileNode, term: string): boolean {
  return (node.children ?? []).some(
    (child) =>
      child.name.toLowerCase().includes(term) ||
      child.path.toLowerCase().includes(term) ||
      subtreeMatches(child, term),
  );
}

function findByPath(node: FileNode, path: string): FileNode | null {
  if (node.path === path) return node;
  for (const child of node.children ?? []) {
    const found = findByPath(child, path);
    if (found) return found;
  }
  return null;
}

function collectFiles(node: FileNode): FileNode[] {
  if (node.type === 'file') return [node];
  return (node.children ?? []).flatMap(collectFiles);
}
