import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { healthRamp, verdictLabel } from '../../core/util/health';

/** Compact score + verdict pill, used wherever a score needs a name. */
@Component({
  selector: 'sdna-verdict-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="dot" [style.background]="color()"></span>
    <span class="label">{{ label() }}</span>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        gap: var(--sp-2);
        padding: 3px var(--sp-3) 3px var(--sp-2);
        border: 1px solid var(--border);
        border-radius: var(--r-full);
        background: var(--surface);
        font-family: var(--font-mono);
        font-size: var(--fs-2xs);
        letter-spacing: var(--tracking-wide);
        text-transform: uppercase;
        color: var(--text-secondary);
        white-space: nowrap;
      }

      .dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        flex: none;
        box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 8%, transparent);
      }
    `,
  ],
})
export class VerdictBadgeComponent {
  readonly score = input.required<number>();
  readonly color = computed(() => healthRamp(this.score()));
  readonly label = computed(() => verdictLabel(this.score()));
}
