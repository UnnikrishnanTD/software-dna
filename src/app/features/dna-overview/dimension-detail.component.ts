import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { DnaDimension } from '../../core/models/dna.model';
import { healthRamp } from '../../core/util/health';
import { DeltaBadgeComponent } from '../../shared/ui/delta-badge.component';
import { DimensionMeterComponent } from '../../shared/ui/dimension-meter.component';
import { IconComponent } from '../../shared/ui/icon.component';
import { NumeralComponent } from '../../shared/ui/numeral.component';
import { VerdictBadgeComponent } from '../../shared/ui/verdict-badge.component';

/**
 * The expanded reading for one dimension: what it scores, why, and what to
 * watch. Presentational — it receives a dimension and emits a close.
 */
@Component({
  selector: 'sdna-dimension-detail',
  standalone: true,
  imports: [
    DeltaBadgeComponent,
    DimensionMeterComponent,
    IconComponent,
    NumeralComponent,
    VerdictBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="head">
      <div>
        <p class="eyebrow">{{ dimension().label }}</p>
        <div class="score-row">
          @if (dimension().score; as score) {
            <span class="score numeral" [style.color]="color()">
              <sdna-numeral [value]="score" [durationMs]="620" />
            </span>
            <span class="denominator mono">/ 100</span>
            <sdna-verdict-badge [score]="score" />
          } @else {
            <!-- The dimension was not assessed. Showing a zero here would
                 report an absence of evidence as a failing score. -->
            <span class="score numeral is-unmeasured">—</span>
            <span class="unmeasured-label">Not measured</span>
          }
        </div>
      </div>
      <button
        type="button"
        class="btn btn-ghost btn-icon"
        aria-label="Close dimension detail"
        (click)="closed.emit()"
      >
        <sdna-icon name="close" [size]="16" />
      </button>
    </header>

    <sdna-dimension-meter
      [score]="dimension().score"
      [previous]="previousScore()"
    />

    <p class="meta">
      <sdna-delta [value]="dimension().delta" unit=" pts" />
      <span class="separator" aria-hidden="true">·</span>
      <span class="weight mono">{{ weightLabel() }} of overall score</span>
    </p>

    <p class="summary">{{ dimension().summary }}</p>

    <section class="findings">
      <h3 class="eyebrow">Strengths</h3>
      <ul role="list">
        @for (item of dimension().strengths; track item) {
          <li class="is-strength">
            <sdna-icon name="check" [size]="12" [strokeWidth]="2.4" />
            <span>{{ item }}</span>
          </li>
        }
      </ul>
    </section>

    @if (dimension().watchItems.length > 0) {
      <section class="findings">
        <h3 class="eyebrow">Watch</h3>
        <ul role="list">
          @for (item of dimension().watchItems; track item) {
            <li class="is-watch">
              <sdna-icon name="alert" [size]="12" [strokeWidth]="2" />
              <span>{{ item }}</span>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styleUrl: './dimension-detail.component.scss',
})
export class DimensionDetailComponent {
  readonly dimension = input.required<DnaDimension>();
  readonly closed = output<void>();

  readonly color = computed(() => {
    const score = this.dimension().score;
    return score === null ? 'var(--text-muted)' : healthRamp(score);
  });

  /** Where the score stood at the previous analysis, if it was measured. */
  readonly previousScore = computed(() => {
    const dimension = this.dimension();
    return dimension.score === null ? null : dimension.score - dimension.delta;
  });

  readonly weightLabel = computed(
    () => `${Math.round(this.dimension().weight * 100)}%`,
  );
}
