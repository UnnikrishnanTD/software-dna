import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  NgZone,
} from '@angular/core';
import { easeOutCubic, prefersReducedMotion } from '../util/motion';

/**
 * An instrument readout that counts up to its value.
 *
 * The animation writes to the host element's text directly rather than
 * through a template binding. Driving it through a signal would mean one
 * change-detection pass per frame per numeral — and on a page with a dozen
 * readouts counting at once, that is the difference between a smooth
 * animation and a stuttering one. Writing the DOM node costs nothing and
 * keeps the whole animation outside Angular entirely.
 */
@Component({
  selector: 'sdna-numeral',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
  host: {
    class: 'numeral',
    role: 'img',
  },
  styles: [
    `
      :host {
        display: inline-block;
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class NumeralComponent {
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly element: HTMLElement =
    inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly value = input.required<number>();
  readonly durationMs = input(900);
  readonly decimals = input(0);
  readonly suffix = input('');
  /** Delay before the count starts, used to stagger a group of readouts. */
  readonly delayMs = input(0);
  /** Names the figure for assistive technology, e.g. "Engineering health". */
  readonly label = input<string>('');

  private displayed = 0;
  private frameId = 0;

  constructor() {
    effect(() => {
      // Read every input that affects rendering so the effect re-runs when
      // any of them changes, not only the value.
      const target = this.value();
      this.decimals();
      this.suffix();
      this.label();
      this.animateTo(target);
    });

    this.destroyRef.onDestroy(() => this.stop());
  }

  private animateTo(target: number): void {
    this.stop();

    if (prefersReducedMotion() || this.durationMs() === 0) {
      this.render(target);
      return;
    }

    const from = this.displayed;
    const start = performance.now() + this.delayMs();
    const duration = this.durationMs();

    this.zone.runOutsideAngular(() => {
      const step = (now: number): void => {
        if (now < start) {
          this.frameId = requestAnimationFrame(step);
          return;
        }
        const t = Math.min((now - start) / duration, 1);
        this.render(from + (target - from) * easeOutCubic(t));
        if (t < 1) this.frameId = requestAnimationFrame(step);
      };
      this.frameId = requestAnimationFrame(step);
    });
  }

  private render(value: number): void {
    this.displayed = value;
    const text = value.toFixed(this.decimals()) + this.suffix();
    this.element.textContent = text;
    const label = this.label();
    this.element.setAttribute(
      'aria-label',
      label ? `${label}: ${text}` : text,
    );
  }

  private stop(): void {
    if (this.frameId) cancelAnimationFrame(this.frameId);
    this.frameId = 0;
  }
}
