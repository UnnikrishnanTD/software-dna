import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { DoctorBlock } from '../../core/models/insight.model';
import { AnalysisStore } from '../../core/services/analysis-store';
import { healthRamp } from '../../core/util/health';
import { DimensionMeterComponent } from '../../shared/ui/dimension-meter.component';
import { IconComponent } from '../../shared/ui/icon.component';

/**
 * Renders a Doctor answer's structured blocks.
 *
 * Answers are blocks rather than prose because a diagnostic tool should
 * show its measurements. A metric renders as a real meter, a remediation
 * plan as a ranked plan, and a node reference as a link into the graph —
 * which is what separates this from a chat window that happens to know
 * some numbers.
 */
@Component({
  selector: 'sdna-doctor-blocks',
  standalone: true,
  imports: [RouterLink, DimensionMeterComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (block of blocks(); track $index) {
      @switch (block.type) {
        @case ('text') {
          <p class="block-text">{{ block.text }}</p>
        }

        @case ('metric') {
          <div class="block-metric">
            <div class="metric-head">
              <span class="metric-label">{{ block.label }}</span>
              <span
                class="metric-value mono"
                [style.color]="color(block.verdictScore)"
              >
                {{ block.value }}
              </span>
            </div>
            <sdna-dimension-meter [score]="block.verdictScore" />
          </div>
        }

        @case ('list') {
          @if (block.ordered) {
            <ol class="block-list is-ordered">
              @for (item of block.items; track item) {
                <li>{{ item }}</li>
              }
            </ol>
          } @else {
            <ul class="block-list" role="list">
              @for (item of block.items; track item) {
                <li>{{ item }}</li>
              }
            </ul>
          }
        }

        @case ('nodes') {
          <div class="block-nodes">
            <p class="nodes-caption">{{ block.caption }}</p>
            <div class="node-links">
              @for (nodeId of block.nodeIds; track nodeId) {
                <a
                  class="node-link mono"
                  routerLink="../architecture"
                  [queryParams]="{ node: nodeId }"
                >
                  <sdna-icon name="architecture" [size]="11" />
                  {{ nodeName(nodeId) }}
                </a>
              }
            </div>
          </div>
        }

        @case ('plan') {
          <ol class="block-plan">
            @for (step of block.steps; track step.rank) {
              <li>
                <span class="step-rank mono">
                  {{ step.rank < 10 ? '0' + step.rank : step.rank }}
                </span>
                <div class="step-body">
                  <div class="step-head">
                    <span class="step-title">{{ step.title }}</span>
                    <span class="step-tags">
                      <span class="tag mono">{{ step.effort }} effort</span>
                      <span class="tag is-gain mono">+{{ step.expectedGain }}</span>
                    </span>
                  </div>
                  <p class="step-detail">{{ step.detail }}</p>
                </div>
              </li>
            }
          </ol>
        }
      }
    }
  `,
  styleUrl: './doctor-blocks.component.scss',
})
export class DoctorBlocksComponent {
  private readonly store = inject(AnalysisStore);

  readonly blocks = input.required<readonly DoctorBlock[]>();

  /** Graph ids resolved to the names people actually recognise. */
  private readonly nodeNames = computed<ReadonlyMap<string, string>>(
    () =>
      new Map(
        (this.store.analysis()?.architecture.nodes ?? []).map((node) => [
          node.id,
          node.name,
        ]),
      ),
  );

  protected nodeName(id: string): string {
    return this.nodeNames().get(id) ?? id;
  }

  protected color(score: number): string {
    return healthRamp(score);
  }
}
