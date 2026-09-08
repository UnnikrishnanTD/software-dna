import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Consistent title block for every screen inside the analysis shell. */
@Component({
  selector: 'sdna-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="titles">
      <p class="eyebrow">{{ eyebrow() }}</p>
      <h1 class="heading-lg">{{ heading() }}</h1>
      @if (description()) {
        <p class="description">{{ description() }}</p>
      }
    </div>
    <div class="actions">
      <ng-content />
    </div>
  `,
  styles: [
    `
      :host {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--sp-6);
        flex-wrap: wrap;
        padding-bottom: var(--sp-6);
      }

      .titles h1 {
        margin-top: var(--sp-2);
      }

      .description {
        margin-top: var(--sp-2);
        max-width: 64ch;
        font-size: var(--fs-sm);
        line-height: var(--lh-relaxed);
        color: var(--text-muted);
      }

      .actions {
        display: flex;
        align-items: center;
        gap: var(--sp-2);
        flex-wrap: wrap;
      }
    `,
  ],
})
export class PageHeaderComponent {
  readonly eyebrow = input.required<string>();
  readonly heading = input.required<string>();
  readonly description = input<string>('');
}
