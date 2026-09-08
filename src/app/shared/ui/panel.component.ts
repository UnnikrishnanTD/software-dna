import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The product's surface primitive: a bordered region with an optional
 * eyebrow heading. Used for every card, inspector and section so elevation
 * and spacing stay consistent.
 */
@Component({
  selector: 'sdna-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (heading()) {
      <header class="panel-header">
        <h2 class="eyebrow">{{ heading() }}</h2>
        <ng-content select="[panelActions]" />
      </header>
    }
    <div class="panel-body" [class.flush]="flush()">
      <ng-content />
    </div>
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        min-width: 0;
        background: var(--surface);
        border: 1px solid var(--border-subtle);
        border-radius: var(--r-lg);
        box-shadow: var(--shadow-inset);
      }

      :host(.elevated) {
        background: var(--surface-elevated);
        box-shadow: var(--shadow-md), var(--shadow-inset);
      }

      :host(.plain) {
        background: transparent;
        border-color: transparent;
        box-shadow: none;
      }

      .panel-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--sp-4);
        padding: var(--sp-4) var(--sp-5);
        border-bottom: 1px solid var(--border-subtle);
        min-height: 44px;
      }

      .panel-header h2 {
        margin: 0;
      }

      .panel-body {
        flex: 1;
        min-height: 0;
        padding: var(--sp-5);
      }

      .panel-body.flush {
        padding: 0;
      }
    `,
  ],
})
export class PanelComponent {
  readonly heading = input<string>('');
  /** Removes body padding, for panels that host their own scroll region. */
  readonly flush = input(false);
}
