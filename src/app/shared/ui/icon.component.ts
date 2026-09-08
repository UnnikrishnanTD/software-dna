import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

/**
 * A small inline icon set drawn as SVG paths.
 *
 * An icon font or an icon package would add a dependency and a network
 * request for roughly twenty glyphs; this ships as part of the bundle and
 * inherits `currentColor` everywhere.
 */
export type IconName =
  | 'overview'
  | 'architecture'
  | 'codebase'
  | 'hotspots'
  | 'dependencies'
  | 'evolution'
  | 'doctor'
  | 'compare'
  | 'search'
  | 'close'
  | 'chevron-right'
  | 'chevron-down'
  | 'arrow-right'
  | 'arrow-up'
  | 'arrow-down'
  | 'check'
  | 'alert'
  | 'info'
  | 'plus'
  | 'minus'
  | 'reset'
  | 'filter'
  | 'external'
  | 'menu'
  | 'file'
  | 'folder'
  | 'send'
  | 'sparkle'
  | 'github';

const PATHS: Readonly<Record<IconName, string>> = {
  overview:
    'M6 3c0 4 12 5 12 9s-12 5-12 9M18 3c0 4-12 5-12 9s12 5 12 9M7.5 7h9M7.5 17h9M6.2 12h11.6',
  architecture:
    'M12 3v4M12 17v4M5.5 10.5 8.8 8.2M15.2 8.2l3.3 2.3M8.8 15.8l-3.3-2.3M18.5 13.5l-3.3 2.3M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5ZM12 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM12 17.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM4.5 9a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM19.5 9a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM4.5 13a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM19.5 13a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z',
  codebase: 'M4 5h6l1.6 2H20v12H4V5Z M8 11h8M8 15h5',
  hotspots:
    'M12 3c1.8 3.2.6 4.8-.6 6.2C10 11 9 12.3 9 14a3 3 0 0 0 6 0c0-1-.3-1.8-.8-2.6 2 .9 3.3 2.9 3.3 5.2A5.5 5.5 0 0 1 12 21a5.5 5.5 0 0 1-5.5-5.5C6.5 10.5 12 9 12 3Z',
  dependencies:
    'M12 3 20 7v10l-8 4-8-4V7l8-4ZM12 3v18M4 7l8 4 8-4',
  evolution: 'M3 18h18M4 15l4-5 4 3 4-7 4 4',
  doctor:
    'M6 3v5a4 4 0 0 0 8 0V3M10 15v1a4 4 0 0 0 8 0v-2M18 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM6 3H4M14 3h2',
  compare: 'M12 3v18M4 7h5v10H4V7ZM15 5h5v14h-5V5Z',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14ZM16 16l4.5 4.5',
  close: 'M6 6l12 12M18 6L6 18',
  'chevron-right': 'M9 5l7 7-7 7',
  'chevron-down': 'M5 9l7 7 7-7',
  'arrow-right': 'M4 12h15M13 6l6 6-6 6',
  'arrow-up': 'M12 20V5M6 11l6-6 6 6',
  'arrow-down': 'M12 4v15M6 13l6 6 6-6',
  check: 'M4 12.5 9.5 18 20 6.5',
  alert: 'M12 3 22 20H2L12 3ZM12 10v5M12 17.5v.5',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM12 11v6M12 7.5v.5',
  plus: 'M12 5v14M5 12h14',
  minus: 'M5 12h14',
  reset: 'M4 12a8 8 0 1 1 2.6 5.9M4 12V6M4 12h6',
  filter: 'M3 5h18l-7 8v6l-4 2v-8L3 5Z',
  external: 'M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5',
  menu: 'M4 7h16M4 12h16M4 17h16',
  file: 'M6 3h8l5 5v13H6V3ZM14 3v5h5',
  folder: 'M4 5h6l1.6 2H20v12H4V5Z',
  send: 'M4 12l16-8-6 16-2.5-6.5L4 12Z',
  sparkle: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3ZM19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z',
  github:
    'M12 2.5a9.5 9.5 0 0 0-3 18.5c.5.1.65-.2.65-.45v-1.7c-2.6.55-3.15-1.15-3.15-1.15-.45-1.1-1.05-1.4-1.05-1.4-.85-.6.07-.58.07-.58.95.07 1.45.97 1.45.97.85 1.45 2.2 1.03 2.75.8.08-.6.32-1.03.6-1.27-2.1-.24-4.3-1.05-4.3-4.65 0-1.03.37-1.87.97-2.53-.1-.24-.42-1.2.1-2.5 0 0 .8-.25 2.6.97a9 9 0 0 1 4.75 0c1.8-1.22 2.6-.97 2.6-.97.52 1.3.2 2.26.1 2.5.6.66.97 1.5.97 2.53 0 3.6-2.2 4.4-4.3 4.64.33.29.63.86.63 1.74v2.58c0 .25.15.55.65.45A9.5 9.5 0 0 0 12 2.5Z',
};

/** Icons whose paths describe a filled shape rather than a stroke. */
const FILLED: readonly IconName[] = ['hotspots', 'sparkle', 'github'];

@Component({
  selector: 'sdna-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      [attr.fill]="isFilled() ? 'currentColor' : 'none'"
      [attr.stroke]="isFilled() ? 'none' : 'currentColor'"
      [attr.stroke-width]="strokeWidth()"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        flex: none;
        line-height: 0;
      }
    `,
  ],
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(16);
  readonly strokeWidth = input(1.6);

  readonly path = computed(() => PATHS[this.name()]);
  readonly isFilled = computed(() => FILLED.includes(this.name()));
}
