import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from './icon.component';

export type StateKind = 'loading' | 'empty' | 'error' | 'no-data' | 'partial';

/**
 * The product's answer to loading, empty, error, no-data and partial
 * states. Having one component means none of these ever fall back to a
 * browser default or an ad-hoc spinner.
 */
@Component({
  selector: 'sdna-state-view',
  standalone: true,
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="state" [attr.role]="kind() === 'error' ? 'alert' : 'status'">
      @if (kind() === 'loading') {
        <div class="pulse" aria-hidden="true">
          <span></span><span></span><span></span>
        </div>
      } @else {
        <div class="glyph" aria-hidden="true">
          <sdna-icon [name]="icon()" [size]="20" />
        </div>
      }

      <p class="title">{{ title() }}</p>
      @if (message()) {
        <p class="message">{{ message() }}</p>
      }
      @if (actionLabel()) {
        <button type="button" class="action" (click)="action.emit()">
          {{ actionLabel() }}
        </button>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 180px;
        padding: var(--sp-8) var(--sp-6);
        width: 100%;
      }

      .state {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        max-width: 34ch;
      }

      .glyph {
        display: grid;
        place-items: center;
        width: 42px;
        height: 42px;
        margin-bottom: var(--sp-4);
        border: 1px solid var(--border);
        border-radius: var(--r-lg);
        background: var(--surface-elevated);
        color: var(--text-muted);
      }

      :host(.is-error) .glyph {
        color: var(--danger);
        border-color: color-mix(in srgb, var(--danger) 30%, var(--border));
        background: var(--danger-soft);
      }

      :host(.is-partial) .glyph {
        color: var(--warning);
        border-color: color-mix(in srgb, var(--warning) 30%, var(--border));
        background: var(--warning-soft);
      }

      .title {
        font-size: var(--fs-md);
        font-weight: 500;
        color: var(--text-primary);
        letter-spacing: var(--tracking-tight);
      }

      .message {
        margin-top: var(--sp-2);
        font-size: var(--fs-sm);
        line-height: var(--lh-relaxed);
        color: var(--text-muted);
      }

      .action {
        margin-top: var(--sp-5);
        padding: var(--sp-2) var(--sp-4);
        border: 1px solid var(--border-strong);
        border-radius: var(--r-md);
        background: var(--surface-elevated);
        color: var(--text-primary);
        font-size: var(--fs-sm);
        transition:
          border-color var(--dur-fast) var(--ease-out),
          background var(--dur-fast) var(--ease-out);
      }

      .action:hover {
        border-color: var(--accent);
        background: var(--accent-soft);
      }

      /* Three ascending bars — a scanning cadence rather than a spinner,
         which matches how the analysis itself is presented. */
      .pulse {
        display: flex;
        align-items: flex-end;
        gap: 4px;
        height: 24px;
        margin-bottom: var(--sp-4);
      }

      .pulse span {
        width: 3px;
        height: 100%;
        border-radius: var(--r-full);
        background: var(--accent);
        transform-origin: bottom;
        animation: pulse 1.1s var(--ease-in-out) infinite;
      }

      .pulse span:nth-child(2) {
        animation-delay: 0.15s;
      }
      .pulse span:nth-child(3) {
        animation-delay: 0.3s;
      }

      @keyframes pulse {
        0%,
        100% {
          transform: scaleY(0.3);
          opacity: 0.35;
        }
        50% {
          transform: scaleY(1);
          opacity: 1;
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .pulse span {
          animation: none;
          transform: scaleY(0.6);
          opacity: 0.6;
        }
      }
    `,
  ],
  host: {
    '[class.is-error]': "kind() === 'error'",
    '[class.is-partial]': "kind() === 'partial'",
  },
})
export class StateViewComponent {
  readonly kind = input.required<StateKind>();
  readonly title = input.required<string>();
  readonly message = input<string>('');
  readonly actionLabel = input<string>('');

  readonly action = output<void>();

  readonly icon = computed(() => {
    switch (this.kind()) {
      case 'error':
        return 'alert' as const;
      case 'partial':
        return 'info' as const;
      case 'no-data':
        return 'minus' as const;
      default:
        return 'search' as const;
    }
  });
}
