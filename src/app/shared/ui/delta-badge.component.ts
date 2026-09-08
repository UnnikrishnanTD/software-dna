import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from './icon.component';

/** Signed change indicator. Neutral when the value has not moved. */
@Component({
  selector: 'sdna-delta',
  standalone: true,
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (value() !== 0) {
      <sdna-icon [name]="value() > 0 ? 'arrow-up' : 'arrow-down'" [size]="11" [strokeWidth]="2.2" />
    }
    <span>{{ text() }}</span>
  `,
  host: {
    '[class.is-up]': 'value() > 0',
    '[class.is-down]': 'value() < 0',
    '[attr.aria-label]': 'ariaLabel()',
  },
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        gap: 3px;
        font-family: var(--font-mono);
        font-size: var(--fs-2xs);
        font-variant-numeric: tabular-nums;
        color: var(--text-muted);
      }
      :host(.is-up) {
        color: var(--success);
      }
      :host(.is-down) {
        color: var(--danger);
      }
    `,
  ],
})
export class DeltaBadgeComponent {
  readonly value = input.required<number>();
  readonly unit = input('');

  readonly text = computed(() => {
    const value = this.value();
    if (value === 0) return `no change`;
    return `${Math.abs(value)}${this.unit()}`;
  });

  readonly ariaLabel = computed(() => {
    const value = this.value();
    if (value === 0) return 'No change since the previous analysis';
    const direction = value > 0 ? 'up' : 'down';
    return `${direction} ${Math.abs(value)}${this.unit()} since the previous analysis`;
  });
}
