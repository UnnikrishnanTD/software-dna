import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ArchitectureNode } from '../../core/models/architecture.model';
import { healthRamp, riskColorVar } from '../../core/util/health';
import { IconComponent } from '../../shared/ui/icon.component';

/** Everything known about one unit, plus its immediate neighbourhood. */
@Component({
  selector: 'sdna-node-inspector',
  standalone: true,
  imports: [RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="head">
      <div class="identity">
        <p class="eyebrow">{{ node().kind }}</p>
        <h2 class="name mono">{{ node().name }}</h2>
        <p class="path mono">{{ node().path }}</p>
      </div>
      <button
        type="button"
        class="btn btn-ghost btn-icon"
        aria-label="Close inspector"
        (click)="closed.emit()"
      >
        <sdna-icon name="close" [size]="16" />
      </button>
    </header>

    <p class="description">{{ node().description }}</p>

    <dl class="metrics">
      <div>
        <dt>Layer</dt>
        <dd>{{ node().layer }}</dd>
      </div>
      <div>
        <dt>Language</dt>
        <dd>{{ node().language }}</dd>
      </div>
      <div>
        <dt>Complexity</dt>
        <dd>{{ node().complexity }} ({{ node().complexityScore }})</dd>
      </div>
      <div>
        <dt>Lines</dt>
        <dd class="mono">{{ node().linesOfCode.toLocaleString() }}</dd>
      </div>
      <div>
        <dt>Depends on</dt>
        <dd class="mono">{{ node().fanOut }}</dd>
      </div>
      <div>
        <dt>Used by</dt>
        <dd class="mono">{{ node().fanIn }}</dd>
      </div>
      <div>
        <dt>Coverage</dt>
        <dd class="mono" [style.color]="coverageColor()">
          {{ node().coverage }}%
        </dd>
      </div>
      <div>
        <dt>Risk</dt>
        <dd class="risk" [style.color]="riskColor()">{{ node().risk }}</dd>
      </div>
    </dl>

    @if (dependsOn().length > 0) {
      <section class="relations">
        <h3 class="eyebrow">Depends on ({{ dependsOn().length }})</h3>
        <ul role="list">
          @for (related of dependsOn(); track related.id) {
            <li>
              <button type="button" class="relation" (click)="navigate.emit(related.id)">
                <span class="mono">{{ related.name }}</span>
                <span class="relation-layer">{{ related.layer }}</span>
              </button>
            </li>
          }
        </ul>
      </section>
    }

    @if (usedBy().length > 0) {
      <section class="relations">
        <h3 class="eyebrow">Used by ({{ usedBy().length }})</h3>
        <ul role="list">
          @for (related of usedBy(); track related.id) {
            <li>
              <button type="button" class="relation" (click)="navigate.emit(related.id)">
                <span class="mono">{{ related.name }}</span>
                <span class="relation-layer">{{ related.layer }}</span>
              </button>
            </li>
          }
        </ul>
      </section>
    }

    <footer class="actions">
      <a
        class="btn btn-sm"
        routerLink="../codebase"
        [queryParams]="{ path: node().path }"
      >
        <sdna-icon name="codebase" [size]="13" />
        Open in codebase
      </a>
    </footer>
  `,
  styleUrl: './node-inspector.component.scss',
})
export class NodeInspectorComponent {
  readonly node = input.required<ArchitectureNode>();
  readonly dependsOn = input.required<readonly ArchitectureNode[]>();
  readonly usedBy = input.required<readonly ArchitectureNode[]>();

  readonly closed = output<void>();
  readonly navigate = output<string>();

  readonly riskColor = computed(() => riskColorVar(this.node().risk));
  readonly coverageColor = computed(() => {
    const coverage = this.node().coverage;
    return coverage === null ? 'var(--text-muted)' : healthRamp(coverage);
  });
}
