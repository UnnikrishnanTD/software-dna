import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { healthRamp } from '../../core/util/health';

interface Tick {
  readonly x: number;
  readonly filled: boolean;
  readonly major: boolean;
}

const TICK_COUNT = 32;
const VIEW_W = 200;
const VIEW_H = 20;

/**
 * A calibrated scale rather than a progress bar.
 *
 * Ticks give the reader a sense of position on a 0–100 range at a glance,
 * the needle marks the current score, and the ghost needle marks where the
 * score sat at the previous analysis — so the delta is visible in the same
 * mark as the value. A filled bar could not carry that third piece of
 * information.
 */
@Component({
  selector: 'sdna-dimension-meter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.viewBox]="'0 0 ' + VIEW_W + ' ' + VIEW_H"
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      @for (tick of ticks(); track tick.x) {
        <line
          [attr.x1]="tick.x"
          [attr.x2]="tick.x"
          [attr.y1]="tick.major ? 3 : 6"
          [attr.y2]="tick.major ? 17 : 14"
          [attr.stroke]="tick.filled ? color() : 'var(--border-strong)'"
          [attr.stroke-opacity]="tick.filled ? (tick.major ? 0.95 : 0.55) : 0.5"
          stroke-width="1.5"
          stroke-linecap="round"
        />
      }

      @if (previous() !== null) {
        <line
          [attr.x1]="previousX()"
          [attr.x2]="previousX()"
          y1="1"
          y2="19"
          stroke="var(--text-faint)"
          stroke-width="1.5"
          stroke-dasharray="2 2"
        />
      }

      @if (measured()) {
        <line
          [attr.x1]="needleX()"
          [attr.x2]="needleX()"
          y1="0"
          y2="20"
          [attr.stroke]="color()"
          stroke-width="2.5"
          stroke-linecap="round"
          class="needle"
        />
      }
    </svg>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }

      svg {
        display: block;
        width: 100%;
        height: 20px;
        overflow: visible;
      }

      .needle {
        transition: transform var(--dur-slower) var(--ease-out);
      }
    `,
  ],
})
export class DimensionMeterComponent {
  protected readonly VIEW_W = VIEW_W;
  protected readonly VIEW_H = VIEW_H;

  /**
   * 0–100, or null when the dimension was never measured. A null score draws
   * an empty scale in a neutral colour rather than a full red bar, because an
   * absent measurement is not a bad one.
   */
  readonly score = input.required<number | null>();
  /** The previous analysis's score, drawn as a ghost needle. */
  readonly previous = input<number | null>(null);

  readonly measured = computed(() => this.score() !== null);

  readonly color = computed(() => {
    const score = this.score();
    return score === null ? 'var(--text-faint)' : healthRamp(score);
  });

  readonly ticks = computed<readonly Tick[]>(() => {
    const score = this.score();
    if (score === null) {
      return Array.from({ length: TICK_COUNT }, (_, index) => ({
        x: (index / (TICK_COUNT - 1)) * VIEW_W,
        filled: false,
        major: index % 4 === 0,
      }));
    }
    const filledUpTo = (score / 100) * (TICK_COUNT - 1);
    return Array.from({ length: TICK_COUNT }, (_, index) => ({
      x: (index / (TICK_COUNT - 1)) * VIEW_W,
      filled: index <= filledUpTo,
      major: index % 4 === 0,
    }));
  });

  readonly needleX = computed(() => ((this.score() ?? 0) / 100) * VIEW_W);
  readonly previousX = computed(() => ((this.previous() ?? 0) / 100) * VIEW_W);
}
